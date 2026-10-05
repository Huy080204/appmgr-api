package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ota.OtaAsset;
import com.appmgr.api.dto.ota.OtaPart;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.model.Channel;
import com.appmgr.api.model.Version;
import com.appmgr.api.repository.ChannelRepository;
import com.appmgr.api.repository.VersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OtaSigningService {

    @Autowired
    private ChannelRepository channelRepository;
    @Autowired
    private VersionRepository versionRepository;
    @Autowired
    private VersionFileService versionFileService;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${server.uri:}")
    private String serverUri;
    @Value("${ota.private-key-path:}")
    private String privateKeyPath;
    @Value("${ota.key-id:main}")
    private String keyId;

    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        privateKey = loadPrivateKey();
    }

    public OtaPart buildManifest(Version version, Long appId, String channelName, String platform) {
        Path versionDir = versionFileService.resolveVersionDir(appId, channelName, version.getId());
        JsonNode platformNode = readFileMetadata(versionDir).path(platform);
        if (!platformNode.isObject()) {
            throw new NotFoundException("Platform not found", ErrorCode.VERSION_ERROR_NOT_FOUND);
        }
        String assetBaseUrl = serverUri + BaseConstant.OTA_ASSETS_PATH + "/" + appId + "/"
                + UriUtils.encodePathSegment(channelName, StandardCharsets.UTF_8) + "/" + version.getId() + "/";
        List<Map<String, Object>> assets = new ArrayList<>();
        for (JsonNode asset : platformNode.path("assets")) {
            String extension = toExtension(asset);
            assets.add(buildFileEntry(versionDir, assetBaseUrl, asset.path("path").asText(""), extension,
                    toMediaType(extension)));
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", version.getManifestId());
        manifest.put("createdAt", Instant.ofEpochMilli(version.getTimestamp()).toString());
        manifest.put("runtimeVersion", version.getRuntimeVersion());
        manifest.put("launchAsset", buildFileEntry(versionDir, assetBaseUrl, platformNode.path("bundle").asText(""),
                BaseConstant.OTA_BUNDLE_EXTENSION, BaseConstant.OTA_MEDIA_TYPE_JAVASCRIPT));
        manifest.put("assets", assets);
        manifest.put("metadata", Collections.emptyMap());
        manifest.put("extra", Collections.emptyMap());
        return sign(BaseConstant.OTA_PART_MANIFEST, toJson(manifest));
    }

    public OtaPart buildNoUpdateDirective() {
        Map<String, Object> directive = new LinkedHashMap<>();
        directive.put("type", BaseConstant.OTA_DIRECTIVE_NO_UPDATE);
        return sign(BaseConstant.OTA_PART_DIRECTIVE, toJson(directive));
    }

    public OtaAsset resolveAsset(String appId, String channelName, String versionId, String filePath) {
        Long applicationId = parseId(appId);
        Long id = parseId(versionId);
        validateFilePath(filePath);
        Channel channel = channelRepository.findByName(channelName).orElseThrow(this::assetNotFound);
        versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(id, applicationId, channel.getId(),
                BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE).orElseThrow(this::assetNotFound);

        Path versionDir;
        try {
            versionDir = versionFileService.resolveVersionDir(applicationId, channel.getName(), id);
        } catch (BadRequestException e) {
            throw assetNotFound();
        }
        Path target = resolveDeclaredFile(versionDir, filePath);
        String mediaType = findDeclaredMediaType(readFileMetadata(versionDir), filePath);
        if (mediaType == null || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !isRealPathInside(versionDir, target)) {
            throw assetNotFound();
        }
        return new OtaAsset(new FileSystemResource(target), mediaType);
    }

    // manifest helpers

    private Map<String, Object> buildFileEntry(Path versionDir, String assetBaseUrl, String relativePath,
                                               String extension, String contentType) {
        byte[][] digests = digest(resolveDeclaredFile(versionDir, relativePath));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("hash", Base64.getUrlEncoder().withoutPadding().encodeToString(digests[0]));
        entry.put("key", String.format("%032x", new BigInteger(1, digests[1])));
        entry.put("fileExtension", extension);
        entry.put("contentType", contentType);
        entry.put("url", assetBaseUrl + UriUtils.encodePath(relativePath, StandardCharsets.UTF_8));
        return entry;
    }

    private byte[][] digest(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest sha256 = MessageDigest.getInstance(BaseConstant.OTA_HASH_SHA256);
            MessageDigest md5 = MessageDigest.getInstance(BaseConstant.OTA_HASH_MD5);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                sha256.update(buffer, 0, read);
                md5.update(buffer, 0, read);
            }
            return new byte[][]{sha256.digest(), md5.digest()};
        } catch (IOException e) {
            throw new NotFoundException("File not found", ErrorCode.VERSION_ERROR_NOT_FOUND);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Digest algorithm unavailable", e);
        }
    }

    private String toExtension(JsonNode asset) {
        String ext = asset.path("ext").asText("");
        return ext.isEmpty() ? "" : "." + ext;
    }

    private String toMediaType(String extension) {
        return MediaTypeFactory.getMediaType("x" + extension)
                .map(MediaType::toString)
                .orElse(BaseConstant.OTA_MEDIA_TYPE_DEFAULT);
    }

    // metadata / paths

    private JsonNode readFileMetadata(Path versionDir) {
        JsonNode fileMetadata;
        try {
            fileMetadata = objectMapper.readTree(versionDir.resolve(BaseConstant.OTA_METADATA_FILE).toFile())
                    .path("fileMetadata");
        } catch (IOException e) {
            throw assetNotFound();
        }
        if (!fileMetadata.isObject()) {
            throw assetNotFound();
        }
        for (JsonNode platform : fileMetadata) {
            if (platform instanceof ObjectNode) {
                normalizeField((ObjectNode) platform, "bundle");
                for (JsonNode asset : platform.path("assets")) {
                    if (asset instanceof ObjectNode) {
                        normalizeField((ObjectNode) asset, "path");
                    }
                }
            }
        }
        return fileMetadata;
    }

    private void normalizeField(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual()) {
            node.put(field, value.asText().replace('\\', '/'));
        }
    }

    private Path resolveDeclaredFile(Path versionDir, String relativePath) {
        Path base = versionDir.toAbsolutePath().normalize();
        Path target;
        try {
            target = base.resolve(relativePath).normalize();
        } catch (InvalidPathException e) {
            throw assetNotFound();
        }
        if (!target.startsWith(base) || target.equals(base)) {
            throw assetNotFound();
        }
        return target;
    }

    private String findDeclaredMediaType(JsonNode fileMetadata, String relativePath) {
        for (JsonNode platform : fileMetadata) {
            if (relativePath.equals(platform.path("bundle").asText(null))) {
                return BaseConstant.OTA_MEDIA_TYPE_JAVASCRIPT;
            }
            for (JsonNode asset : platform.path("assets")) {
                if (relativePath.equals(asset.path("path").asText(null))) {
                    return toMediaType(toExtension(asset));
                }
            }
        }
        return null;
    }

    // asset string

    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty() || filePath.startsWith("/") || filePath.indexOf('\\') >= 0
                || filePath.indexOf(':') >= 0 || filePath.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw assetNotFound();
        }
        for (String segment : filePath.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw assetNotFound();
            }
        }
    }

    private boolean isRealPathInside(Path base, Path target) {
        try {
            return target.toRealPath().startsWith(base.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw assetNotFound();
        }
    }

    private NotFoundException assetNotFound() {
        return new NotFoundException("Asset not found", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    // signing

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OTA payload", e);
        }
    }

    private OtaPart sign(String name, String json) {
        if (privateKey == null) {
            return new OtaPart(name, json, null);
        }
        try {
            Signature signature = Signature.getInstance(BaseConstant.OTA_SIGNATURE_JCA_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(json.getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(signature.sign());
            return new OtaPart(name, json, "sig=\"" + encoded + "\", keyid=\"" + keyId + "\", alg=\""
                    + BaseConstant.OTA_SIGNATURE_ALGORITHM + "\"");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign OTA payload", e);
        }
    }

    private PrivateKey loadPrivateKey() {
        if (privateKeyPath.trim().isEmpty()) {
            log.warn("ota.private-key-path is not configured, OTA responses will be unsigned");
            return null;
        }
        try {
            String pem = new String(Files.readAllBytes(Path.of(privateKeyPath.trim())), StandardCharsets.UTF_8);
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Cannot load OTA private key ({}), OTA responses will be unsigned", e.getClass().getSimpleName());
            return null;
        }
    }
}

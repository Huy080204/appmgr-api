package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.mapper.VersionMapper;
import com.appmgr.api.model.Version;
import com.appmgr.api.repository.VersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class OtaService {

    @Autowired
    private VersionRepository versionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VersionMapper versionMapper;

    @Autowired
    private VersionFileService versionFileService;

    @Transactional
    public Version publish(Version version, MultipartFile file) {
        validateOtaZip(file);
        versionRepository.save(version);
        Path versionDir = versionFileService.resolveVersionDir(
                version.getApplication().getId(), version.getChannel().getName(), version.getId());
        try {
            extractOtaZip(file, versionDir);
            version.setManifestId(computeManifestId(versionDir.resolve(BaseConstant.OTA_METADATA_FILE)));
        } catch (BadRequestException e) {
            versionFileService.deleteVersionDir(versionDir);
            throw e;
        }
        versionRepository.save(version);
        return version;
    }

    @Transactional
    public Version republish(Version source) {
        Long appId = source.getApplication().getId();
        String channelName = source.getChannel().getName();
        Path sourceDir = versionFileService.resolveVersionDir(appId, channelName, source.getId());
        if (!Files.isRegularFile(sourceDir.resolve(BaseConstant.OTA_METADATA_FILE))) {
            throw new BadRequestException("Source OTA metadata not found", ErrorCode.VERSION_ERROR_METADATA_NOT_FOUND);
        }

        Version version = versionMapper.fromEntityToRepublishEntity(source);
        version.setTimestamp(Instant.now().toEpochMilli());
        versionRepository.save(version);

        Path targetDir = versionFileService.resolveVersionDir(appId, channelName, version.getId());
        boolean success = false;
        try {
            FileSystemUtils.copyRecursively(sourceDir, targetDir);
            Path metadataFile = targetDir.resolve(BaseConstant.OTA_METADATA_FILE);
            JsonNode metadata = objectMapper.readTree(metadataFile.toFile());
            if (!metadata.isObject()) {
                throw new BadRequestException("Invalid OTA metadata", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
            }
            ((ObjectNode) metadata).put(BaseConstant.OTA_METADATA_REPUBLISHED_FROM, String.valueOf(source.getId()));
            ((ObjectNode) metadata).put(BaseConstant.OTA_METADATA_REPUBLISHED_AT, String.valueOf(version.getTimestamp()));
            Files.write(metadataFile, objectMapper.writeValueAsBytes(metadata));
            version.setManifestId(computeManifestId(metadataFile));
            success = true;
        } catch (IOException e) {
            log.error("Republish OTA failed: sourceId={}", source.getId(), e);
            throw new BadRequestException("Republish OTA failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        } finally {
            if (!success) {
                versionFileService.deleteVersionDir(targetDir);
            }
        }
        versionRepository.save(version);
        return version;
    }

    private void validateOtaZip(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(BaseConstant.OTA_EXTENSION_ZIP)) {
            throw new BadRequestException("OTA file must be a zip");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("OTA file is required");
        }
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && BaseConstant.OTA_METADATA_FILE.equals(entry.getName())) {
                    return;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        } catch (IOException e) {
            log.error("Read OTA zip failed", e);
            throw new BadRequestException("Read OTA zip failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
        throw new BadRequestException("metadata.json not found at zip root", ErrorCode.VERSION_ERROR_METADATA_NOT_FOUND);
    }

    private void extractOtaZip(MultipartFile file, Path versionDir) {
        Path targetDir = versionDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                Path target = targetDir.resolve(entryName).normalize();
                if (!target.startsWith(targetDir) || target.equals(targetDir)) {
                    throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
                }
                if (entryName.endsWith("/")) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (ZipException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        } catch (IOException e) {
            log.error("Extract OTA zip failed", e);
            throw new BadRequestException("Extract OTA zip failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
    }

    private String computeManifestId(Path metadataFile) {
        try {
            byte[] digest = MessageDigest.getInstance(BaseConstant.OTA_HASH_SHA256).digest(Files.readAllBytes(metadataFile));
            String hex = toHex(digest).substring(0, 32);
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Compute manifest id failed", e);
            throw new BadRequestException("Compute manifest id failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

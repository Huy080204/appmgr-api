package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ota.OtaAsset;
import com.appmgr.api.dto.ota.OtaPart;
import com.appmgr.api.dto.project.ProjectKeyDto;
import com.appmgr.api.dto.project.ProjectSettingsDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Channel;
import com.appmgr.api.model.Project;
import com.appmgr.api.model.Version;
import com.appmgr.api.repository.ProjectRepository;
import com.appmgr.api.repository.VersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaSigningServiceTest {
    private static final String SERVER_URI = "http://localhost:8787";
    private static final String KEY_ID = "main";
    private static final String SETTINGS_JSON = "{\"keys\":{}}";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Long APP_ID = 100L;
    private static final Long CHANNEL_ID = 7L;
    private static final Long VERSION_ID = 300L;
    private static final String CHANNEL_NAME = "production";
    private static final String VERSION = String.valueOf(VERSION_ID);
    private static final Long TIMESTAMP = 1700000000000L;
    private static final String RUNTIME_VERSION = "1.0.0";
    private static final String MANIFEST_ID = "6f1d2c3b-4a5e-4f60-8a7b-9c0d1e2f3a4b";

    private static final String ANDROID_BUNDLE = "_expo/static/js/android/index-abc.hbc";
    private static final String ANDROID_ASSET = "assets/def";
    private static final String IOS_BUNDLE = "_expo/static/js/ios/index-xyz.hbc";
    private static final String IOS_ASSET = "assets/ghi";
    private static final byte[] ANDROID_BUNDLE_BYTES = "android-bundle-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ANDROID_ASSET_BYTES = "asset-def-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IOS_BUNDLE_BYTES = "ios-bundle-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IOS_ASSET_BYTES = "asset-ghi-bytes".getBytes(StandardCharsets.UTF_8);

    private static final Pattern SIGNATURE_HEADER = Pattern.compile("^sig=\"([^\"]+)\", keyid=\"main\", alg=\"rsa-v1_5-sha256\"$");

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectSettingsService projectSettingsService;
    @Mock
    private VersionFileService versionFileService;
    @InjectMocks
    private OtaSigningService service;

    @TempDir
    Path versionDir;
    @TempDir
    Path keyDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "serverUri", SERVER_URI);
    }

    // ---------------------------------------------------------------- buildManifest

    @Test
    void shouldBuildManifestFieldsWhenAndroidPlatformDeclared() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(part.getName()).isEqualTo(BaseConstant.OTA_PART_MANIFEST);
        assertThat(root.get("id").asText()).isEqualTo(MANIFEST_ID);
        assertThat(root.get("createdAt").asText()).isEqualTo(Instant.ofEpochMilli(TIMESTAMP).toString());
        assertThat(root.get("runtimeVersion").asText()).isEqualTo(RUNTIME_VERSION);
        assertThat(root.get("metadata").isObject()).isTrue();
        assertThat(root.get("metadata").size()).isZero();
        assertThat(root.get("extra").isObject()).isTrue();
        assertThat(root.get("extra").size()).isZero();
    }

    @Test
    void shouldBuildLaunchAssetWhenBundleDeclared() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode launchAsset = objectMapper.readTree(part.getJson()).get("launchAsset");
        assertThat(launchAsset.get("hash").asText()).isEqualTo(sha256Base64Url(ANDROID_BUNDLE_BYTES));
        assertThat(launchAsset.get("key").asText()).isEqualTo(md5Hex(ANDROID_BUNDLE_BYTES));
        assertThat(launchAsset.get("fileExtension").asText()).isEqualTo(".bundle");
        assertThat(launchAsset.get("contentType").asText()).isEqualTo("application/javascript");
        assertThat(launchAsset.get("url").asText()).isEqualTo(assetUrl(ANDROID_BUNDLE));
    }

    @Test
    void shouldBuildAssetEntryWhenAssetDeclared() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode assets = objectMapper.readTree(part.getJson()).get("assets");
        assertThat(assets.size()).isEqualTo(1);
        assertThat(assets.get(0).get("hash").asText()).isEqualTo(sha256Base64Url(ANDROID_ASSET_BYTES));
        assertThat(assets.get(0).get("key").asText()).isEqualTo(md5Hex(ANDROID_ASSET_BYTES));
        assertThat(assets.get(0).get("fileExtension").asText()).isEqualTo(".png");
        assertThat(assets.get(0).get("contentType").asText()).isEqualTo("image/png");
        assertThat(assets.get(0).get("url").asText()).isEqualTo(assetUrl(ANDROID_ASSET));
    }

    @Test
    void shouldUseIosFilesWhenPlatformIsIos() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_IOS);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(root.get("launchAsset").get("key").asText()).isEqualTo(md5Hex(IOS_BUNDLE_BYTES));
        assertThat(root.get("launchAsset").get("url").asText()).isEqualTo(assetUrl(IOS_BUNDLE));
        assertThat(root.get("assets").get(0).get("key").asText()).isEqualTo(md5Hex(IOS_ASSET_BYTES));
        assertThat(root.get("assets").get(0).get("url").asText()).isEqualTo(assetUrl(IOS_ASSET));
    }

    @Test
    void shouldNotExposeServerPathWhenBuildingUrls() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        String tempDirName = versionDir.getFileName().toString();
        assertThat(part.getJson()).doesNotContain(tempDirName);
        assertThat(part.getJson()).doesNotContain(URLEncoder.encode(tempDirName, StandardCharsets.UTF_8));
    }

    @Test
    void shouldBuildPathStyleUrlsWithoutQueryOrEncodedSlashesWhenBuildingManifest() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(root.get("launchAsset").get("url").asText())
                .isEqualTo("http://localhost:8787/v1/ota/assets/300/_expo/static/js/android/index-abc.hbc");
        assertThat(part.getJson()).doesNotContain("?asset=");
        assertThat(part.getJson()).doesNotContain("APP_VERSION");
        assertThat(part.getJson()).doesNotContain("%2F");
    }

    @Test
    void shouldEncodeFilePathSegmentWhenAssetPathHasSpace() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformFiles(ANDROID_BUNDLE, "assets/a b")));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile("assets/a b", ANDROID_ASSET_BYTES);
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode entry = objectMapper.readTree(part.getJson()).get("assets").get(0);
        assertThat(entry.get("url").asText()).isEqualTo(assetUrl("assets/a%20b"));
    }

    @Test
    void shouldBuildManifestWhenMetadataPathsUseBackslashes() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformFiles("_expo\\static\\js\\android\\index-abc.hbc", "assets\\abc")));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile("assets/abc", ANDROID_ASSET_BYTES);
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(root.get("launchAsset").get("url").asText()).isEqualTo(assetUrl(ANDROID_BUNDLE));
        assertThat(root.get("assets").get(0).get("url").asText()).isEqualTo(assetUrl("assets/abc"));
        assertThat(part.getJson()).doesNotContain("%5C");
    }

    @Test
    void shouldThrowNotFoundWhenPlatformMissingInFileMetadata() throws Exception {
        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put(BaseConstant.OTA_PLATFORM_ANDROID, platformFiles(ANDROID_BUNDLE, ANDROID_ASSET));
        writeMetadata(fileMetadata);
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_IOS))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredBundleMissingOnDisk() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_BUNDLE));
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredAssetMissingOnDisk() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_ASSET));
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldBuildEmptyAssetsArrayAndLaunchAssetWhenNoAssetsDeclared() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith(ANDROID_BUNDLE, Collections.<Map<String, Object>>emptyList())));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(root.get("assets").isArray()).isTrue();
        assertThat(root.get("assets").size()).isZero();
        assertThat(root.get("launchAsset").get("url").asText()).isEqualTo(assetUrl(ANDROID_BUNDLE));
    }

    @Test
    void shouldUseEmptyExtensionAndDefaultContentTypeWhenAssetExtMissing() throws Exception {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("path", ANDROID_ASSET);
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith(ANDROID_BUNDLE, Collections.singletonList(asset))));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode entry = objectMapper.readTree(part.getJson()).get("assets").get(0);
        assertThat(entry.get("fileExtension").asText()).isEmpty();
        assertThat(entry.get("contentType").asText()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldUseEmptyExtensionAndDefaultContentTypeWhenAssetExtEmpty() throws Exception {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("path", ANDROID_ASSET);
        asset.put("ext", "");
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith(ANDROID_BUNDLE, Collections.singletonList(asset))));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode entry = objectMapper.readTree(part.getJson()).get("assets").get(0);
        assertThat(entry.get("fileExtension").asText()).isEmpty();
        assertThat(entry.get("contentType").asText()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldThrowNotFoundWhenPlatformNodeIsJsonNull() throws Exception {
        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put(BaseConstant.OTA_PLATFORM_ANDROID, null);
        writeMetadata(fileMetadata);
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileMissingOnBuildManifest() {
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessage("Asset not found")
                .hasMessageNotContaining(versionDir.toString());
    }

    @Test
    void shouldThrowNotFoundWhenMetadataDeclaresBundleOutsideVersionDir() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith("../x", Collections.<Map<String, Object>>emptyList())));
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessageNotContaining(versionDir.toString());
    }

    @Test
    void shouldThrowNotFoundWhenMetadataDeclaresAbsoluteAssetPath() throws Exception {
        Path outside = keyDir.resolve("outside.bin");
        Files.write(outside, ANDROID_ASSET_BYTES);
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("path", outside.toAbsolutePath().toString());
        asset.put("ext", "png");
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith(ANDROID_BUNDLE, Collections.singletonList(asset))));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessageNotContaining(versionDir.toString());
    }

    @Test
    void shouldThrowNotFoundWhenMetadataDeclaresEmptyBundle() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith("", Collections.<Map<String, Object>>emptyList())));
        stubVersionDir();
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessageNotContaining(versionDir.toString());
    }

    // ---------------------------------------------------------------- signing

    @Test
    void shouldSignManifestWithChannelJsonKeyWhenSettingsHoldPemKey() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(part.getSignatureHeader()).contains("keyid=\"" + KEY_ID + "\"");
        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldSignManifestWhenKeyIsSingleLineBase64WithoutHeaders() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        stubSigning(Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded()));

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, buildChannel(), BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenKeyIsPkcs1RsaPrivateKeyPem() {
        stubSigning(pkcs1PemPrivateKey());

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenManifestHasNoMatchingKey() throws Exception {
        writeStandardFixture();
        stubVersionDir();

        assertSigningKeyNotFound(() -> service.buildManifest(buildVersion(), APP_ID, buildChannel(),
                BaseConstant.OTA_PLATFORM_ANDROID));
    }

    // ---------------------------------------------------------------- buildNoUpdateDirective

    @Test
    void shouldBuildSignedDirectiveWhenSettingsHoldKey() throws Exception {
        stubSigning(pemPrivateKey());

        OtaPart part = service.buildNoUpdateDirective(APP_ID, buildChannel());

        assertThat(part.getName()).isEqualTo(BaseConstant.OTA_PART_DIRECTIVE);
        assertThat(part.getJson()).isEqualTo("{\"type\":\"noUpdateAvailable\"}");
        assertThat(part.getSignatureHeader()).contains("keyid=\"" + KEY_ID + "\"");
        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenChannelJsonKeyIsNull() {
        Channel channel = buildChannel();
        channel.setJsonKey(null);

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, channel));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenChannelJsonKeyIsBlank() {
        Channel channel = buildChannel();
        channel.setJsonKey("  ");

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, channel));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenProjectMissing() {
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.empty());

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenProjectHasNoSettings() {
        Project project = new Project();
        project.setSettings(null);
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(project));

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenSettingsHaveNoEntryForJsonKey() {
        stubSettings(Collections.singletonList(keyDto("other-key", pemPrivateKey())));

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenKeysListIsNull() {
        stubSettings(null);

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenKeysListIsEmpty() {
        stubSettings(Collections.<ProjectKeyDto>emptyList());

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldPickKeyWhoseKeyIdMatchesChannelJsonKeyAmongSeveral() throws Exception {
        stubSettings(List.of(keyDto("other-1", "garbage-1"), keyDto(KEY_ID, pemPrivateKey()),
                keyDto("other-2", "garbage-2")));

        OtaPart part = service.buildNoUpdateDirective(APP_ID, buildChannel());

        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldUseFirstMatchingElementWhenKeyIdIsDuplicated() throws Exception {
        stubSettings(List.of(keyDto(KEY_ID, pemPrivateKey()), keyDto(KEY_ID, "not a pem key")));

        OtaPart part = service.buildNoUpdateDirective(APP_ID, buildChannel());

        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldSignWhenSettingsParsedByRealServiceFromMultiLinePemJson() throws Exception {
        String pem = pemPrivateKey();
        String publicKey = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
        String json = "{\"keys\":[{\"keyId\":\"" + KEY_ID + "\",\"privateKey\":\""
                + pem.replace("\n", "\\n") + "\",\"publicKey\":\"" + publicKey + "\"}]}";
        Project project = new Project();
        project.setSettings(json);
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(project));
        ProjectSettingsService realParser = new ProjectSettingsService();
        ReflectionTestUtils.setField(realParser, "objectMapper", objectMapper);
        when(projectSettingsService.fromJson(json)).thenReturn(realParser.fromJson(json));

        OtaPart part = service.buildNoUpdateDirective(APP_ID, buildChannel());

        assertThat(json).contains("\\n");
        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldSignWhenSettingsTextHasRawCrLfInsideKeyStringsOfSecondElement() throws Exception {
        String pem = pemPrivateKey().replace("\n", "\r\n");
        String publicKey = "-----BEGIN PUBLIC KEY-----\r\n"
                + Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded())
                + "\r\n-----END PUBLIC KEY-----";
        String json = "{\"keys\":[{\"keyId\":\"other\",\"privateKey\":\"x\",\"publicKey\":\"y\"},"
                + "{\"keyId\":\"" + KEY_ID + "\",\"privateKey\":\"" + pem + "\",\"publicKey\":\"" + publicKey + "\"}]}";
        ProjectSettingsService realService = new ProjectSettingsService();
        ReflectionTestUtils.setField(realService, "objectMapper", objectMapper);
        Project project = new Project();
        project.setSettings(json);
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(project));
        when(projectSettingsService.fromJson(json)).thenReturn(realService.fromJson(json));

        OtaPart part = service.buildNoUpdateDirective(APP_ID, buildChannel());

        assertThat(json).contains("\r\n");
        assertThat(verifies(KEY_PAIR, part)).isTrue();
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenSettingsTextIsNotValidJson() {
        Project project = new Project();
        project.setSettings("{not json");
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(project));
        when(projectSettingsService.fromJson("{not json"))
                .thenThrow(new IllegalStateException("Cannot parse project settings"));

        assertThatThrownBy(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenPrivateKeyIsBlank() {
        stubSigning("   ");

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenPrivateKeyIsMalformed() {
        stubSigning("not a pem key");

        assertSigningKeyNotFound(() -> service.buildNoUpdateDirective(APP_ID, buildChannel()));
    }

    // ---------------------------------------------------------------- resolveAsset

    @Test
    void shouldResolveBundleWhenAssetIsDeclaredBundle() throws Exception {
        writeStandardFixture();
        stubLookup();
        stubVersionDir();

        OtaAsset result = service.resolveAsset(VERSION,ANDROID_BUNDLE);

        assertThat(result.getMediaType()).isEqualTo("application/javascript");
        assertThat(result.getResource().getInputStream().readAllBytes()).isEqualTo(ANDROID_BUNDLE_BYTES);
    }

    @Test
    void shouldResolveAssetWhenAssetIsDeclaredImage() throws Exception {
        writeStandardFixture();
        stubLookup();
        stubVersionDir();

        OtaAsset result = service.resolveAsset(VERSION,ANDROID_ASSET);

        assertThat(result.getMediaType()).isEqualTo("image/png");
        assertThat(result.getResource().getInputStream().readAllBytes()).isEqualTo(ANDROID_ASSET_BYTES);
    }

    @Test
    void shouldResolveAssetWhenMetadataUsesBackslashesAndRequestUsesSlashes() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformFiles("_expo\\static\\js\\android\\index-abc.hbc", "assets\\abc")));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile("assets/abc", ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();

        OtaAsset result = service.resolveAsset(VERSION,"assets/abc");

        assertThat(result.getMediaType()).isEqualTo("image/png");
        assertThat(result.getResource().exists()).isTrue();
    }

    @Test
    void shouldResolveBundleWhenMetadataUsesBackslashesAndRequestUsesSlashes() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformFiles("_expo\\static\\js\\android\\index-abc.hbc", "assets\\abc")));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile("assets/abc", ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();

        OtaAsset result = service.resolveAsset(VERSION,ANDROID_BUNDLE);

        assertThat(result.getMediaType()).isEqualTo("application/javascript");
        assertThat(result.getResource().exists()).isTrue();
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsDotDotSegment() {

        assertResolveNotFound("../secret");
    }

    @Test
    void shouldThrowNotFoundWhenAssetStartsWithSlash() {

        assertResolveNotFound("/" + ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsBackslash() {

        assertResolveNotFound("assets\\def");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsColon() {

        assertResolveNotFound("C:/windows/win.ini");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsEmptySegment() {

        assertResolveNotFound("assets//def");
    }

    @Test
    void shouldThrowNotFoundWhenVersionIdIsNotNumeric() {

        assertResolveNotFound("abc", ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionIdIsNull() {

        assertResolveNotFound(null, ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionIsAbsentOrNotActiveOtaVersion() {
        when(versionRepository.findByIdAndApplicationTypeAndStatus(
                VERSION_ID, BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenFileExistsButIsNotDeclaredInMetadata() throws Exception {
        writeStandardFixture();
        writeFile("assets/secret", "secret-bytes".getBytes(StandardCharsets.UTF_8));
        stubLookup();
        stubVersionDir();

        assertResolveNotFound("assets/secret");
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredPathIsNotARegularFile() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_ASSET));
        Files.createDirectories(versionDir.resolve(ANDROID_ASSET));
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredAssetIsSymlinkToFileOutsideVersionDirAndSkippedWhenSymlinkNotSupported()
            throws Exception {
        writeStandardFixture();
        Path outside = keyDir.resolve("outside-secret.bin");
        Files.write(outside, "secret".getBytes(StandardCharsets.UTF_8));
        Path link = versionDir.resolve(ANDROID_ASSET);
        Files.delete(link);
        Assumptions.assumeTrue(createSymlink(link, outside),
                "symlink cannot be created on this platform/privilege - test skipped");
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsNewline() {

        assertResolveNotFound("assets/de\nf");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsTab() {

        assertResolveNotFound("assets/de\tf");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsNulChar() {

        assertResolveNotFound("assets/de\u0000f");
    }

    @Test
    void shouldThrowNotFoundWhenAssetIsNull() {

        assertResolveNotFound(null);
    }

    @Test
    void shouldThrowNotFoundWhenAssetIsEmpty() {

        assertResolveNotFound("");
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileMissingOnResolveAsset() {
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileIsMalformed() throws Exception {
        Files.write(versionDir.resolve(BaseConstant.OTA_METADATA_FILE), "not json".getBytes(StandardCharsets.UTF_8));
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenFileMetadataIsNotAnObject() throws Exception {
        Files.write(versionDir.resolve(BaseConstant.OTA_METADATA_FILE),
                "{\"fileMetadata\":\"oops\"}".getBytes(StandardCharsets.UTF_8));
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenRequestedPathIsNotDeclaredBecauseMetadataDeclaresOutsidePath() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith("../x", Collections.<Map<String, Object>>emptyList())));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        stubLookup();
        stubVersionDir();

        assertResolveNotFound(ANDROID_BUNDLE);
    }

    // ---------------------------------------------------------------- helpers

    private void assertResolveNotFound(String filePath) {
        assertResolveNotFound(VERSION, filePath);
    }

    private void assertResolveNotFound(String versionId, String filePath) {
        assertThatThrownBy(() -> service.resolveAsset(versionId, filePath))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessage("Asset not found")
                .hasMessageNotContaining(versionDir.toString());
    }

    private boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        }
    }

    private void stubSigning(String privateKey) {
        stubSettings(Collections.singletonList(keyDto(KEY_ID, privateKey)));
    }

    private void stubSettings(List<ProjectKeyDto> keys) {
        Project project = new Project();
        project.setSettings(SETTINGS_JSON);
        ProjectSettingsDto settings = new ProjectSettingsDto();
        settings.setKeys(keys);
        when(projectRepository.findByApplicationId(APP_ID)).thenReturn(Optional.of(project));
        when(projectSettingsService.fromJson(SETTINGS_JSON)).thenReturn(settings);
    }

    private ProjectKeyDto keyDto(String keyId, String privateKey) {
        ProjectKeyDto dto = new ProjectKeyDto();
        dto.setKeyId(keyId);
        dto.setPrivateKey(privateKey);
        dto.setPublicKey("public-key");
        return dto;
    }

    private void assertSigningKeyNotFound(ThrowingCallable call) {
        assertThatThrownBy(call)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    private Channel buildChannel() {
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setName(CHANNEL_NAME);
        channel.setJsonKey(KEY_ID);
        return channel;
    }

    private Version buildVersion() {
        Application application = new Application();
        application.setId(APP_ID);
        application.setType(BaseConstant.VERSION_TYPE_OTA);
        Version version = new Version();
        version.setApplication(application);
        version.setChannel(buildChannel());
        version.setId(VERSION_ID);
        version.setStatus(BaseConstant.STATUS_ACTIVE);
        version.setTimestamp(TIMESTAMP);
        version.setRuntimeVersion(RUNTIME_VERSION);
        version.setManifestId(MANIFEST_ID);
        return version;
    }

    private void stubLookup() {
        when(versionRepository.findByIdAndApplicationTypeAndStatus(
                VERSION_ID, BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(buildVersion()));
    }

    private void stubVersionDir() {
        when(versionFileService.resolveVersionDir(APP_ID, CHANNEL_ID, VERSION_ID)).thenReturn(versionDir);
    }

    private void writeStandardFixture() throws IOException {
        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put(BaseConstant.OTA_PLATFORM_ANDROID, platformFiles(ANDROID_BUNDLE, ANDROID_ASSET));
        fileMetadata.put(BaseConstant.OTA_PLATFORM_IOS, platformFiles(IOS_BUNDLE, IOS_ASSET));
        writeMetadata(fileMetadata);
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        writeFile(IOS_BUNDLE, IOS_BUNDLE_BYTES);
        writeFile(IOS_ASSET, IOS_ASSET_BYTES);
    }

    private Map<String, Object> platformWith(String bundle, List<Map<String, Object>> assets) {
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("bundle", bundle);
        platform.put("assets", assets);
        return platform;
    }

    private Map<String, Object> platformFiles(String bundle, String assetPath) {
        Map<String, Object> asset = new LinkedHashMap<>();
        asset.put("path", assetPath);
        asset.put("ext", "png");
        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("bundle", bundle);
        platform.put("assets", Collections.singletonList(asset));
        return platform;
    }

    private void writeMetadata(Map<String, Object> fileMetadata) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 0);
        root.put("bundler", "metro");
        root.put("fileMetadata", fileMetadata);
        Files.write(versionDir.resolve(BaseConstant.OTA_METADATA_FILE), objectMapper.writeValueAsBytes(root));
    }

    private void writeFile(String relativePath, byte[] bytes) throws IOException {
        Path file = versionDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String pemPrivateKey() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(KEY_PAIR.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
    }

    private String pkcs1PemPrivateKey() {
        byte[] pkcs8 = KEY_PAIR.getPrivate().getEncoded();
        // PKCS#8 header of a 2048-bit RSA key: 4 (SEQUENCE) + 3 (version) + 15 (algorithm) + 4 (OCTET STRING)
        byte[] pkcs1 = Arrays.copyOfRange(pkcs8, 26, pkcs8.length);
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(pkcs1);
        return "-----BEGIN RSA PRIVATE KEY-----\n" + body + "\n-----END RSA PRIVATE KEY-----";
    }

    private boolean verifies(KeyPair keyPair, OtaPart part) throws Exception {
        Matcher matcher = SIGNATURE_HEADER.matcher(String.valueOf(part.getSignatureHeader()));
        if (!matcher.matches()) {
            return false;
        }
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(part.getJson().getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(matcher.group(1)));
    }

    private String assetUrl(String relativePath) {
        return SERVER_URI + "/v1/ota/assets/" + VERSION + "/" + relativePath;
    }

    private String sha256Base64Url(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String md5Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        return String.format("%032x", new BigInteger(1, digest));
    }
}

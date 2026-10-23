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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
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
    private static final Long APP_ID = 100L;
    private static final Long CHANNEL_ID = 7L;
    private static final Long VERSION_ID = 300L;
    private static final String CHANNEL_NAME = "production";
    private static final String APP = String.valueOf(APP_ID);
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
    private ChannelRepository channelRepository;
    @Mock
    private VersionRepository versionRepository;
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
        ReflectionTestUtils.setField(service, "keyId", KEY_ID);
    }

    // ---------------------------------------------------------------- buildManifest

    @Test
    void shouldBuildManifestFieldsWhenAndroidPlatformDeclared() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_IOS);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        String tempDirName = versionDir.getFileName().toString();
        assertThat(part.getJson()).doesNotContain(tempDirName);
        assertThat(part.getJson()).doesNotContain(URLEncoder.encode(tempDirName, StandardCharsets.UTF_8));
    }

    @Test
    void shouldBuildPathStyleUrlsWithoutQueryOrEncodedSlashesWhenBuildingManifest() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode root = objectMapper.readTree(part.getJson());
        assertThat(root.get("launchAsset").get("url").asText())
                .isEqualTo("http://localhost:8787/v1/ota/assets/100/production/300/_expo/static/js/android/index-abc.hbc");
        assertThat(part.getJson()).doesNotContain("?asset=");
        assertThat(part.getJson()).doesNotContain("APP_VERSION");
        assertThat(part.getJson()).doesNotContain("%2F");
    }

    @Test
    void shouldEncodeChannelNameSegmentWhenChannelNameHasSpace() throws Exception {
        writeStandardFixture();
        when(versionFileService.resolveVersionDir(APP_ID, "my chan", VERSION_ID)).thenReturn(versionDir);
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, "my chan", BaseConstant.OTA_PLATFORM_ANDROID);

        JsonNode launchAsset = objectMapper.readTree(part.getJson()).get("launchAsset");
        assertThat(launchAsset.get("url").asText()).isEqualTo(
                SERVER_URI + "/v1/ota/assets/100/my%20chan/300/" + ANDROID_BUNDLE);
    }

    @Test
    void shouldEncodeFilePathSegmentWhenAssetPathHasSpace() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformFiles(ANDROID_BUNDLE, "assets/a b")));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        writeFile("assets/a b", ANDROID_ASSET_BYTES);
        stubVersionDir();
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_IOS))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredBundleMissingOnDisk() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_BUNDLE));
        stubVersionDir();
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredAssetMissingOnDisk() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_ASSET));
        stubVersionDir();
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldBuildEmptyAssetsArrayAndLaunchAssetWhenNoAssetsDeclared() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith(ANDROID_BUNDLE, Collections.<Map<String, Object>>emptyList())));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        stubVersionDir();
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

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
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileMissingOnBuildManifest() {
        stubVersionDir();
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
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
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
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
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessageNotContaining(versionDir.toString());
    }

    @Test
    void shouldThrowNotFoundWhenMetadataDeclaresEmptyBundle() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith("", Collections.<Map<String, Object>>emptyList())));
        stubVersionDir();
        startService("");
        Version version = buildVersion();

        assertThatThrownBy(() -> service.buildManifest(version, APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND)
                .hasMessageNotContaining(versionDir.toString());
    }

    // ---------------------------------------------------------------- signing

    @Test
    void shouldSignManifestWhenPrivateKeyConfigured() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        KeyPair keyPair = generateKeyPair();
        startService(writePrivateKey(keyPair).toString());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(verifies(keyPair, part)).isTrue();
    }

    @Test
    void shouldReturnNullSignatureWhenKeyPathNotConfigured() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        startService("");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(part.getSignatureHeader()).isNull();
        assertThat(part.getJson()).isNotBlank();
    }

    @Test
    void shouldReturnNullSignatureWhenKeyPathBlank() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        startService("   ");

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(part.getSignatureHeader()).isNull();
        assertThat(part.getJson()).isNotBlank();
    }

    @Test
    void shouldReturnNullSignatureWhenKeyFileIsGarbage() throws Exception {
        writeStandardFixture();
        stubVersionDir();
        Path garbage = keyDir.resolve("garbage.pem");
        Files.write(garbage, "not a pem key".getBytes(StandardCharsets.UTF_8));
        startService(garbage.toString());

        OtaPart part = service.buildManifest(buildVersion(), APP_ID, CHANNEL_NAME, BaseConstant.OTA_PLATFORM_ANDROID);

        assertThat(part.getSignatureHeader()).isNull();
        assertThat(part.getJson()).isNotBlank();
    }

    // ---------------------------------------------------------------- buildNoUpdateDirective

    @Test
    void shouldBuildSignedDirectiveWhenPrivateKeyConfigured() throws Exception {
        KeyPair keyPair = generateKeyPair();
        startService(writePrivateKey(keyPair).toString());

        OtaPart part = service.buildNoUpdateDirective();

        assertThat(part.getName()).isEqualTo(BaseConstant.OTA_PART_DIRECTIVE);
        assertThat(part.getJson()).isEqualTo("{\"type\":\"noUpdateAvailable\"}");
        assertThat(verifies(keyPair, part)).isTrue();
    }

    @Test
    void shouldBuildUnsignedDirectiveWhenKeyPathNotConfigured() {
        startService("");

        OtaPart part = service.buildNoUpdateDirective();

        assertThat(part.getName()).isEqualTo(BaseConstant.OTA_PART_DIRECTIVE);
        assertThat(part.getJson()).isEqualTo("{\"type\":\"noUpdateAvailable\"}");
        assertThat(part.getSignatureHeader()).isNull();
    }

    // ---------------------------------------------------------------- resolveAsset

    @Test
    void shouldResolveBundleWhenAssetIsDeclaredBundle() throws Exception {
        writeStandardFixture();
        stubLookup();
        stubVersionDir();
        startService("");

        OtaAsset result = service.resolveAsset(APP, CHANNEL_NAME, VERSION, ANDROID_BUNDLE);

        assertThat(result.getMediaType()).isEqualTo("application/javascript");
        assertThat(result.getResource().getInputStream().readAllBytes()).isEqualTo(ANDROID_BUNDLE_BYTES);
    }

    @Test
    void shouldResolveAssetWhenAssetIsDeclaredImage() throws Exception {
        writeStandardFixture();
        stubLookup();
        stubVersionDir();
        startService("");

        OtaAsset result = service.resolveAsset(APP, CHANNEL_NAME, VERSION, ANDROID_ASSET);

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
        startService("");

        OtaAsset result = service.resolveAsset(APP, CHANNEL_NAME, VERSION, "assets/abc");

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
        startService("");

        OtaAsset result = service.resolveAsset(APP, CHANNEL_NAME, VERSION, ANDROID_BUNDLE);

        assertThat(result.getMediaType()).isEqualTo("application/javascript");
        assertThat(result.getResource().exists()).isTrue();
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsDotDotSegment() {
        startService("");

        assertResolveNotFound("../secret");
    }

    @Test
    void shouldThrowNotFoundWhenAssetStartsWithSlash() {
        startService("");

        assertResolveNotFound("/" + ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsBackslash() {
        startService("");

        assertResolveNotFound("assets\\def");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsColon() {
        startService("");

        assertResolveNotFound("C:/windows/win.ini");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsEmptySegment() {
        startService("");

        assertResolveNotFound("assets//def");
    }

    @Test
    void shouldThrowNotFoundWhenAppIdIsNotNumeric() {
        startService("");

        assertResolveNotFound("abc", CHANNEL_NAME, VERSION, ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionIdIsNotNumeric() {
        startService("");

        assertResolveNotFound(APP, CHANNEL_NAME, "abc", ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenAppIdIsNull() {
        startService("");

        assertResolveNotFound(null, CHANNEL_NAME, VERSION, ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionIdIsNull() {
        startService("");

        assertResolveNotFound(APP, CHANNEL_NAME, null, ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenChannelIsUnknown() {
        when(channelRepository.findByName(CHANNEL_NAME)).thenReturn(Optional.empty());
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionIsAbsentOrNotActiveOtaVersion() {
        Channel channel = buildChannel();
        when(channelRepository.findByName(CHANNEL_NAME)).thenReturn(Optional.of(channel));
        when(versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                VERSION_ID, APP_ID, CHANNEL_ID, BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenVersionDirCannotBeResolved() {
        stubLookup();
        when(versionFileService.resolveVersionDir(APP_ID, CHANNEL_NAME, VERSION_ID))
                .thenThrow(new BadRequestException("Invalid channel name", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID));
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenFileExistsButIsNotDeclaredInMetadata() throws Exception {
        writeStandardFixture();
        writeFile("assets/secret", "secret-bytes".getBytes(StandardCharsets.UTF_8));
        stubLookup();
        stubVersionDir();
        startService("");

        assertResolveNotFound("assets/secret");
    }

    @Test
    void shouldThrowNotFoundWhenDeclaredPathIsNotARegularFile() throws Exception {
        writeStandardFixture();
        Files.delete(versionDir.resolve(ANDROID_ASSET));
        Files.createDirectories(versionDir.resolve(ANDROID_ASSET));
        stubLookup();
        stubVersionDir();
        startService("");

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
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsNewline() {
        startService("");

        assertResolveNotFound("assets/de\nf");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsTab() {
        startService("");

        assertResolveNotFound("assets/de\tf");
    }

    @Test
    void shouldThrowNotFoundWhenAssetContainsNulChar() {
        startService("");

        assertResolveNotFound("assets/de\u0000f");
    }

    @Test
    void shouldThrowNotFoundWhenAssetIsNull() {
        startService("");

        assertResolveNotFound(null);
    }

    @Test
    void shouldThrowNotFoundWhenAssetIsEmpty() {
        startService("");

        assertResolveNotFound("");
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileMissingOnResolveAsset() {
        stubLookup();
        stubVersionDir();
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenMetadataFileIsMalformed() throws Exception {
        Files.write(versionDir.resolve(BaseConstant.OTA_METADATA_FILE), "not json".getBytes(StandardCharsets.UTF_8));
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenFileMetadataIsNotAnObject() throws Exception {
        Files.write(versionDir.resolve(BaseConstant.OTA_METADATA_FILE),
                "{\"fileMetadata\":\"oops\"}".getBytes(StandardCharsets.UTF_8));
        writeFile(ANDROID_ASSET, ANDROID_ASSET_BYTES);
        stubLookup();
        stubVersionDir();
        startService("");

        assertResolveNotFound(ANDROID_ASSET);
    }

    @Test
    void shouldThrowNotFoundWhenRequestedPathIsNotDeclaredBecauseMetadataDeclaresOutsidePath() throws Exception {
        writeMetadata(Collections.<String, Object>singletonMap(BaseConstant.OTA_PLATFORM_ANDROID,
                platformWith("../x", Collections.<Map<String, Object>>emptyList())));
        writeFile(ANDROID_BUNDLE, ANDROID_BUNDLE_BYTES);
        stubLookup();
        stubVersionDir();
        startService("");

        assertResolveNotFound(ANDROID_BUNDLE);
    }

    @Test
    void shouldBuildVersionDirFromDatabaseChannelNameWhenRequestChannelDiffersInCase() throws Exception {
        writeStandardFixture();
        when(channelRepository.findByName("Production")).thenReturn(Optional.of(buildChannel()));
        when(versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                VERSION_ID, APP_ID, CHANNEL_ID, BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(buildVersion()));
        stubVersionDir();
        startService("");

        OtaAsset result = service.resolveAsset(APP, "Production", VERSION, ANDROID_ASSET);

        assertThat(result.getMediaType()).isEqualTo("image/png");
        assertThat(result.getResource().getInputStream().readAllBytes()).isEqualTo(ANDROID_ASSET_BYTES);
    }

    // ---------------------------------------------------------------- helpers

    private void assertResolveNotFound(String filePath) {
        assertResolveNotFound(APP, CHANNEL_NAME, VERSION, filePath);
    }

    private void assertResolveNotFound(String appId, String channelName, String versionId, String filePath) {
        assertThatThrownBy(() -> service.resolveAsset(appId, channelName, versionId, filePath))
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

    private void startService(String privateKeyPath) {
        ReflectionTestUtils.setField(service, "privateKeyPath", privateKeyPath);
        service.init();
    }

    private Channel buildChannel() {
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setName(CHANNEL_NAME);
        return channel;
    }

    private Version buildVersion() {
        Version version = new Version();
        version.setId(VERSION_ID);
        version.setType(BaseConstant.VERSION_TYPE_OTA);
        version.setStatus(BaseConstant.STATUS_ACTIVE);
        version.setTimestamp(TIMESTAMP);
        version.setRuntimeVersion(RUNTIME_VERSION);
        version.setManifestId(MANIFEST_ID);
        return version;
    }

    private void stubLookup() {
        when(channelRepository.findByName(CHANNEL_NAME)).thenReturn(Optional.of(buildChannel()));
        when(versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                VERSION_ID, APP_ID, CHANNEL_ID, BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(buildVersion()));
    }

    private void stubVersionDir() {
        when(versionFileService.resolveVersionDir(APP_ID, CHANNEL_NAME, VERSION_ID)).thenReturn(versionDir);
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

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path writePrivateKey(KeyPair keyPair) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
        Path keyFile = keyDir.resolve("private.pem");
        Files.write(keyFile, pem.getBytes(StandardCharsets.UTF_8));
        return keyFile;
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
        return SERVER_URI + "/v1/ota/assets/" + APP + "/" + CHANNEL_NAME + "/" + VERSION + "/" + relativePath;
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

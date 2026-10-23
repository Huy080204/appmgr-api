package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Category;
import com.appmgr.api.model.Channel;
import com.appmgr.api.mapper.VersionMapper;
import com.appmgr.api.model.Version;
import org.mapstruct.factory.Mappers;
import com.appmgr.api.repository.VersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtaServiceTest {

    private static final String UUID_REGEX =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @TempDir
    Path tempDir;

    @Mock private VersionRepository versionRepository;

    private Path root;
    private VersionFileService versionFileService;
    private OtaService otaService;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        versionFileService = spy(new VersionFileService());
        ReflectionTestUtils.setField(versionFileService, "rootDirectory", root.toString());
        otaService = new OtaService();
        ReflectionTestUtils.setField(otaService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(otaService, "versionMapper", Mappers.getMapper(VersionMapper.class));
        ReflectionTestUtils.setField(otaService, "versionRepository", versionRepository);
        ReflectionTestUtils.setField(otaService, "versionFileService", versionFileService);
    }

    @Test
    void shouldExtractZipAndSetManifestIdWhenOtaZipIsValid() throws IOException, NoSuchAlgorithmException {
        stubSaveAssignsId();
        byte[] metadata = "{\"version\":0,\"bundler\":\"metro\"}".getBytes(StandardCharsets.UTF_8);
        byte[] hbc = new byte[]{9, 8, 7};
        byte[] asset = new byte[]{4, 5, 6};
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", metadata);
        entries.put("_expo/static/js/android/index-abc.hbc", hbc);
        entries.put("assets/0f1e2d", asset);
        MockMultipartFile file = zipFile("ota.zip", entries);

        Version result = otaService.publish(version(), file);

        Path versionDir = versionDir();
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getManifestId()).isEqualTo(expectedManifestId(metadata)).matches(UUID_REGEX);
        assertThat(Files.readAllBytes(versionDir.resolve("metadata.json"))).isEqualTo(metadata);
        assertThat(Files.readAllBytes(versionDir.resolve("_expo/static/js/android/index-abc.hbc"))).isEqualTo(hbc);
        assertThat(Files.readAllBytes(versionDir.resolve("assets/0f1e2d"))).isEqualTo(asset);
        verify(versionRepository, times(2)).save(any(Version.class));
    }

    @Test
    void shouldThrowBadRequestWithoutCodeWhenFileIsNotZip() {
        MockMultipartFile file = new MockMultipartFile("file", "ota.rar", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldThrowBadRequestWithoutCodeWhenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "ota.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldThrowMetadataNotFoundWithoutWritingWhenMetadataIsOnlyNested() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("dist/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_METADATA_NOT_FOUND);
        verify(versionRepository, never()).save(any(Version.class));
        assertRootIsEmpty();
    }

    @Test
    void shouldThrowZipEntryInvalidWithoutSavingWhenEntryNameIsMalformedBeforeMetadata() throws IOException {
        MockMultipartFile file = zipWithMalformedEntryName(false);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        verify(versionRepository, never()).save(any(Version.class));
        assertRootIsEmpty();
    }

    @Test
    void shouldThrowZipEntryInvalidAndWriteNothingOutsideWhenEntryIsZipSlip() throws IOException {
        stubSaveAssignsId();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        try (Stream<Path> all = Files.walk(tempDir)) {
            assertThat(all.filter(p -> p.getFileName().toString().equals("evil.txt")).count()).isZero();
        }
    }

    @Test
    void shouldExtractIntoSubfoldersWhenEntryNamesUseBackslashes() throws IOException {
        stubSaveAssignsId();
        byte[] hbc = new byte[]{9, 8, 7};
        byte[] asset = new byte[]{4, 5, 6};
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("assets\\", new byte[0]);
        entries.put("assets\\def", asset);
        entries.put("_expo\\static\\js\\android\\index-abc.hbc", hbc);
        MockMultipartFile file = zipFile("ota.zip", entries);

        otaService.publish(version(), file);

        Path versionDir = versionDir();
        assertThat(versionDir.resolve("assets")).isDirectory();
        assertThat(Files.readAllBytes(versionDir.resolve("assets/def"))).isEqualTo(asset);
        assertThat(Files.readAllBytes(versionDir.resolve("_expo/static/js/android/index-abc.hbc"))).isEqualTo(hbc);
        try (Stream<Path> all = Files.walk(versionDir)) {
            assertThat(all.filter(p -> p.getFileName().toString().contains("\\")).count()).isZero();
        }
    }

    @Test
    void shouldThrowZipEntryInvalidWhenEntryIsZipSlipWithBackslashes() throws IOException {
        stubSaveAssignsId();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("..\\evil.txt", "evil".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        try (Stream<Path> all = Files.walk(tempDir)) {
            assertThat(all.filter(p -> p.getFileName().toString().contains("evil")).count()).isZero();
        }
    }

    @Test
    void shouldDeletePartialDirWhenExtractionFails() throws IOException {
        stubSaveAssignsId();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("../evil.txt", "evil".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file)).isInstanceOf(BadRequestException.class);
        assertThat(versionDir()).doesNotExist();
    }

    @Test
    void shouldThrowZipEntryInvalidWhenEntryResolvesToTargetDir() throws IOException {
        stubSaveAssignsId();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("./", new byte[0]);
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void shouldThrowZipEntryInvalidWhenEntryNameHasInvalidPathChar() throws IOException {
        stubSaveAssignsId();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        entries.put("a\u0000b.txt", new byte[]{1});
        MockMultipartFile file = zipFile("ota.zip", entries);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void shouldThrowZipEntryInvalidWhenEntryNameIsMalformedAfterMetadata() throws IOException {
        stubSaveAssignsId();
        MockMultipartFile file = zipWithMalformedEntryName(true);

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void shouldThrowZipEntryInvalidWhenEntryCrcIsCorrupt() throws IOException {
        stubSaveAssignsId();
        MockMultipartFile file = zipWithCorruptCrc();

        assertThatThrownBy(() -> otaService.publish(version(), file))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void shouldCopyEveryFileAndStampMetadataWhenRepublishingSource() throws IOException, NoSuchAlgorithmException {
        stubSaveAssignsNewId();
        byte[] metadata = "{\"version\":0,\"bundler\":\"metro\"}".getBytes(StandardCharsets.UTF_8);
        byte[] hbc = new byte[]{9, 8, 7};
        byte[] asset = new byte[]{4, 5, 6};
        Path sourceDir = sourceDir();
        writeFile(sourceDir.resolve("metadata.json"), metadata);
        writeFile(sourceDir.resolve("_expo/static/js/android/index-abc.hbc"), hbc);
        writeFile(sourceDir.resolve("assets/0f1e2d"), asset);
        Version source = sourceVersion();

        Version result = otaService.republish(source);

        Path targetDir = versionDir(6L);
        JsonNode copied = new ObjectMapper().readTree(Files.readAllBytes(targetDir.resolve("metadata.json")));
        assertThat(result.getId()).isEqualTo(6L);
        assertThat(Files.readAllBytes(targetDir.resolve("_expo/static/js/android/index-abc.hbc"))).isEqualTo(hbc);
        assertThat(Files.readAllBytes(targetDir.resolve("assets/0f1e2d"))).isEqualTo(asset);
        assertThat(Files.readAllBytes(sourceDir.resolve("metadata.json"))).isEqualTo(metadata);
        assertThat(copied.get("version").asInt()).isZero();
        assertThat(copied.get("bundler").asText()).isEqualTo("metro");
        assertThat(copied.get("_republishedFrom").asText()).isEqualTo("5");
        assertThat(copied.get("_republishedAt").asText()).isEqualTo(String.valueOf(result.getTimestamp()));
        assertThat(result.getManifestId())
                .matches(UUID_REGEX)
                .isNotEqualTo(expectedManifestId(metadata))
                .isEqualTo(expectedManifestId(Files.readAllBytes(targetDir.resolve("metadata.json"))));
        verify(versionRepository, times(2)).save(any(Version.class));
    }

    @Test
    void shouldCopySourceFieldsToNewVersionWhenRepublishing() throws IOException {
        stubSaveAssignsNewId();
        writeFile(sourceDir().resolve("metadata.json"), "{}".getBytes(StandardCharsets.UTF_8));
        Version source = sourceVersion();

        Version result = otaService.republish(source);

        assertThat(result).isNotSameAs(source);
        assertThat(result.getType()).isEqualTo(BaseConstant.VERSION_TYPE_OTA);
        assertThat(result.getApplication()).isSameAs(source.getApplication());
        assertThat(result.getCategory()).isSameAs(source.getCategory());
        assertThat(result.getChannel()).isSameAs(source.getChannel());
        assertThat(result.getRuntimeVersion()).isEqualTo("1.0");
        assertThat(result.getVersionCode()).isEqualTo(10);
        assertThat(result.getVersionName()).isEqualTo("1.0.0");
        assertThat(result.getMinVersion()).isEqualTo(3);
        assertThat(result.getRequiredUpdate()).isTrue();
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    void shouldThrowMetadataNotFoundBeforeSavingWhenSourceHasNoMetadata() throws IOException {
        Files.createDirectories(sourceDir());

        assertThatThrownBy(() -> otaService.republish(sourceVersion()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_METADATA_NOT_FOUND);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldDeleteTargetDirWhenCopiedMetadataIsNotJsonObject() throws IOException {
        stubSaveAssignsNewId();
        writeFile(sourceDir().resolve("metadata.json"), "[]".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> otaService.republish(sourceVersion()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        verify(versionFileService).deleteVersionDir(versionDir(6L));
        assertThat(versionDir(6L)).doesNotExist();
    }

    @Test
    void shouldDeleteTargetDirWhenCopiedMetadataIsUnreadable() throws IOException {
        stubSaveAssignsNewId();
        writeFile(sourceDir().resolve("metadata.json"), "not json".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> otaService.republish(sourceVersion()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        verify(versionFileService).deleteVersionDir(versionDir(6L));
        assertThat(versionDir(6L)).doesNotExist();
    }

    private void stubSaveAssignsNewId() {
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> {
            Version version = invocation.getArgument(0);
            if (version.getId() == null) {
                version.setId(6L);
            }
            return version;
        });
    }

    private Path sourceDir() {
        return versionDir(5L);
    }

    private Path versionDir(Long id) {
        return root.resolve(BaseConstant.VERSION_FOLDER).resolve("1").resolve("prod").resolve(String.valueOf(id));
    }

    private static void writeFile(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private Version sourceVersion() {
        Version source = version();
        source.setId(5L);
        source.setCategory(new Category());
        source.setRuntimeVersion("1.0");
        source.setMinVersion(3);
        source.setRequiredUpdate(true);
        source.setManifestId("old-manifest");
        return source;
    }

    private void stubSaveAssignsId() {
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> {
            Version version = invocation.getArgument(0);
            version.setId(5L);
            return version;
        });
    }

    private Path versionDir() {
        return root.resolve(BaseConstant.VERSION_FOLDER).resolve("1").resolve("prod").resolve("5");
    }

    private void assertRootIsEmpty() throws IOException {
        try (Stream<Path> children = Files.list(root)) {
            assertThat(children.count()).isZero();
        }
    }

    private Version version() {
        Application application = new Application();
        application.setId(1L);
        application.setName("MyApp");
        Channel channel = new Channel();
        channel.setId(3L);
        channel.setName("prod");
        Version version = new Version();
        version.setType(BaseConstant.VERSION_TYPE_OTA);
        version.setVersionCode(10);
        version.setVersionName("1.0.0");
        version.setApplication(application);
        version.setChannel(channel);
        return version;
    }

    private static MockMultipartFile zipFile(String name, Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", name, "application/zip", bos.toByteArray());
    }

    private static MockMultipartFile zipWithMalformedEntryName(boolean metadataFirst) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (metadataFirst) {
            entries.put("metadata.json", "{}".getBytes(StandardCharsets.UTF_8));
        }
        entries.put("XYZ.txt", new byte[]{1});
        byte[] bytes = zipFile("ota.zip", entries).getBytes();
        int nameOffset = indexOf(bytes, "XYZ".getBytes(StandardCharsets.US_ASCII));
        bytes[nameOffset] = (byte) 0xFF;
        bytes[nameOffset + 1] = (byte) 0xFE;
        bytes[nameOffset + 2] = (byte) 0xFD;
        return new MockMultipartFile("file", "ota.zip", "application/zip", bytes);
    }

    private static MockMultipartFile zipWithCorruptCrc() throws IOException {
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(content);
        ZipEntry entry = new ZipEntry("metadata.json");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(crc.getValue());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(entry);
            zos.write(content);
            zos.closeEntry();
        }
        byte[] bytes = bos.toByteArray();
        bytes[14] = (byte) ~bytes[14];
        return new MockMultipartFile("file", "ota.zip", "application/zip", bytes);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return i;
            }
        }
        throw new IllegalStateException("needle not found");
    }

    private static String expectedManifestId(byte[] metadata) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(metadata);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String hex = sb.substring(0, 32);
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
    }
}

package com.appmgr.api.service;

import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Channel;
import com.appmgr.api.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionFileServiceTest {

    @TempDir
    Path tempDir;

    private Path root;
    private VersionFileService versionFileService;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        versionFileService = new VersionFileService();
        ReflectionTestUtils.setField(versionFileService, "rootDirectory", root.toString());
    }

    @Test
    void resolveBundleExtension_apk_returnsApk() {
        assertThat(versionFileService.resolveBundleExtension("a.apk")).isEqualTo("apk");
    }

    @Test
    void resolveBundleExtension_upperCaseTarGz_returnsTarGz() {
        assertThat(versionFileService.resolveBundleExtension("A.TAR.GZ")).isEqualTo("tar.gz");
    }

    @Test
    void resolveBundleExtension_zip_returnsNull() {
        assertThat(versionFileService.resolveBundleExtension("a.zip")).isNull();
    }

    @Test
    void storeBundle_writesFileAndCreatesParentDirs() throws IOException {
        byte[] content = "bundle-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "a.apk", "application/octet-stream", content);
        Path versionDir = root.resolve("APP_VERSION/1/production/15");

        versionFileService.storeBundle(file, versionDir, "MyApp_1.0.0_10.apk");

        Path stored = versionDir.resolve("MyApp_1.0.0_10.apk");
        assertThat(stored).exists().isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void storeBundle_parentTraversalFileName_throwsFileInvalidAndWritesNothingOutside() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.apk", null, new byte[]{1});
        Path versionDir = root.resolve("APP_VERSION/1/production/15");

        assertThatThrownBy(() -> versionFileService.storeBundle(file, versionDir, "../../evil.apk"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isNull();
        assertNoFileNamed("evil.apk");
    }

    @Test
    void storeBundle_nestedTraversalFileName_throwsFileInvalidAndWritesNothingOutside() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "a.apk", null, new byte[]{1});
        Path versionDir = root.resolve("APP_VERSION/1/production/15");

        assertThatThrownBy(() -> versionFileService.storeBundle(file, versionDir, "a/../../b.apk"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isNull();
        assertNoFileNamed("b.apk");
    }

    @Test
    void storeBundle_invalidPathCharInFileName_throwsFileInvalid() {
        MockMultipartFile file = new MockMultipartFile("file", "a.apk", null, new byte[]{1});
        Path versionDir = root.resolve("APP_VERSION/1/production/15");

        assertThatThrownBy(() -> versionFileService.storeBundle(file, versionDir, "a\u0000b.apk"))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isNull();
    }

    @Test
    void resolveVersionDir_buildsRootAppVersionPath() {
        Path dir = versionFileService.resolveVersionDir(1L, "production", 15L);

        assertThat(dir.normalize())
                .isEqualTo(root.resolve("APP_VERSION").resolve("1").resolve("production").resolve("15").normalize());
    }

    @Test
    void resolveVersionDir_traversalChannelName_throwsZipEntryInvalid() {
        assertThatThrownBy(() -> versionFileService.resolveVersionDir(1L, "../x", 15L))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void resolveVersionDir_invalidPathCharInChannelName_throwsZipEntryInvalid() {
        assertThatThrownBy(() -> versionFileService.resolveVersionDir(1L, "a\u0000b", 15L))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
    }

    @Test
    void deleteVersionDir_deletesRecursively() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("APP_VERSION/1/production/15/assets"));
        Files.write(versionDir.resolve("a.bin"), new byte[]{1});
        Files.write(versionDir.getParent().resolve("metadata.json"), new byte[]{2});
        Path target = versionDir.getParent();

        versionFileService.deleteVersionDir(target);

        assertThat(target).doesNotExist();
    }

    @Test
    void deleteVersionDir_missingDir_doesNotThrow() {
        Path missing = root.resolve("APP_VERSION/9/none/99");

        assertThatCode(() -> versionFileService.deleteVersionDir(missing)).doesNotThrowAnyException();
    }

    @Test
    void storeVersionBundle_savedVersion_writesNamedFileUnderVersionDir() throws IOException {
        byte[] content = "bundle-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "app.tar.gz", "application/gzip", content);

        versionFileService.storeVersionBundle(savedBundleVersion(), file);

        Path stored = root.resolve("APP_VERSION/1/production/15/MyApp_1.0.0_10.tar.gz");
        assertThat(stored).exists().isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void storeVersionBundle_storeFails_deletesVersionDirAndRethrows() throws IOException {
        Path versionDir = root.resolve("APP_VERSION/1/production/15");
        Path blockingDir = Files.createDirectories(versionDir.resolve("MyApp_1.0.0_10.apk"));
        Files.write(blockingDir.resolve("a.bin"), new byte[]{1});
        MockMultipartFile file = new MockMultipartFile("file", "app.apk", null, new byte[]{1});

        assertThatThrownBy(() -> versionFileService.storeVersionBundle(savedBundleVersion(), file))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        assertThat(versionDir).doesNotExist();
    }

    private Version savedBundleVersion() {
        Application application = new Application();
        application.setId(1L);
        application.setName("MyApp");
        Channel channel = new Channel();
        channel.setId(3L);
        channel.setName("production");
        Version version = new Version();
        version.setId(15L);
        version.setApplication(application);
        version.setChannel(channel);
        version.setVersionName("1.0.0");
        version.setVersionCode(10);
        return version;
    }

    private void assertNoFileNamed(String fileName) throws IOException {
        try (Stream<Path> all = Files.walk(tempDir)) {
            assertThat(all.filter(p -> p.getFileName().toString().equals(fileName)).count()).isZero();
        }
    }
}

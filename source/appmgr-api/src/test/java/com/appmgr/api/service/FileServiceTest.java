package com.appmgr.api.service;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.file.UploadFileDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.form.file.UploadFileForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileServiceTest {

    @TempDir
    Path tempDir;

    private Path root;
    private Path secret;
    private final FileService fileService = new FileService();

    @BeforeEach
    void setUp() throws IOException {
        root = tempDir.resolve("uploads");
        Files.createDirectories(root.resolve("AVATAR"));
        secret = tempDir.resolve("secret.txt");
        Files.write(secret, "top secret".getBytes(StandardCharsets.UTF_8));
        ReflectionTestUtils.setField(fileService, "ROOT_DIRECTORY", root.toString());
    }

    private Path avatar(String name) throws IOException {
        Path file = root.resolve("AVATAR").resolve(name);
        Files.write(file, "img".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private UploadFileForm form(String type, String originalName) {
        UploadFileForm form = new UploadFileForm();
        form.setType(type);
        form.setFile(new MockMultipartFile("file", originalName, "application/octet-stream", "data".getBytes(StandardCharsets.UTF_8)));
        return form;
    }

    // ---- download

    @Test
    void shouldReturnFileWhenItIsInsideUploadFolder() throws IOException {
        avatar("a.png");

        Resource resource = fileService.loadFileAsResource("AVATAR", "a.png");

        assertThat(resource).isNotNull();
        assertThat(resource.getFilename()).isEqualTo("a.png");
    }

    @Test
    void shouldReturnNullWhenFileNameTraversesOutOfFolder() {
        assertThat(fileService.loadFileAsResource("AVATAR", "../../secret.txt")).isNull();
        assertThat(fileService.loadFileAsResource("AVATAR", "..\\..\\secret.txt")).isNull();
        assertThat(fileService.loadFileAsResource("AVATAR", "/../../secret.txt")).isNull();
    }

    @Test
    void shouldReturnNullWhenFolderIsNotAnUploadType() throws IOException {
        Files.createDirectories(root.resolve("private"));
        Files.write(root.resolve("private").resolve("x.txt"), "x".getBytes(StandardCharsets.UTF_8));

        assertThat(fileService.loadFileAsResource("private", "x.txt")).isNull();
        assertThat(fileService.loadFileAsResource("..", "secret.txt")).isNull();
        assertThat(fileService.loadFileAsResource(".", "AVATAR")).isNull();
    }

    @Test
    void shouldReturnNullWhenTargetIsADirectory() throws IOException {
        Files.createDirectories(root.resolve("AVATAR").resolve("sub"));

        assertThat(fileService.loadFileAsResource("AVATAR", "sub")).isNull();
        assertThat(fileService.loadFileAsResource("AVATAR", ".")).isNull();
    }

    @Test
    void shouldReturnNullWhenSymlinkPointsOutsideFolder() throws IOException {
        Path link = root.resolve("AVATAR").resolve("link.png");
        try {
            Files.createSymbolicLink(link, secret);
        } catch (IOException | UnsupportedOperationException e) {
            assumeTrue(false, "symlinks not available on this machine");
        }

        assertThat(fileService.loadFileAsResource("AVATAR", "link.png")).isNull();
    }

    // ---- upload

    @Test
    void shouldStoreImageUnderTypeFolderWithGeneratedNameWhenUploadingAvatar() throws IOException {
        ApiMessageDto<UploadFileDto> result = fileService.storeFile(form("AVATAR", "me.PNG"));

        assertThat(result.getResult()).isTrue();
        String stored = result.getData().getFilePath().replace('\\', '/');
        assertThat(stored).matches("/AVATAR/AVATAR_[A-Za-z0-9]{10}\\.PNG");
        assertThat(Files.isRegularFile(root.resolve(stored.substring(1)))).isTrue();
    }

    @Test
    void shouldIgnoreDirectoriesInOriginalNameWhenUploading() throws IOException {
        ApiMessageDto<UploadFileDto> result = fileService.storeFile(form("AVATAR", "../../../evil/x.png"));

        String stored = result.getData().getFilePath().replace('\\', '/');
        assertThat(stored).startsWith("/AVATAR/AVATAR_");
        assertThat(Files.list(tempDir).filter(Files::isDirectory)).containsExactly(root);
    }

    @Test
    void shouldCheckExtensionWhenTypeIsSentInLowerCase() {
        assertThatThrownBy(() -> fileService.storeFile(form("avatar", "shell.jsp")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> fileService.storeFile(form("logo", "page.html")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldRejectUnknownType() {
        assertThatThrownBy(() -> fileService.storeFile(form("../etc", "a.png")))
                .isInstanceOf(BadRequestException.class);
    }

    // ---- delete

    @Test
    void shouldDeleteFileWhenItIsInsideUploadFolder() throws IOException {
        Path file = avatar("a.png");

        fileService.deleteFile("/AVATAR/a.png");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void shouldDeleteFileWhenPathUsesBackslashes() throws IOException {
        Path file = avatar("b.png");

        fileService.deleteFile("\\AVATAR\\b.png");

        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void shouldKeepFilesOutsideUploadFoldersWhenStoredPathIsMalicious() throws IOException {
        Path other = root.resolve("other.txt");
        Files.write(other, "x".getBytes(StandardCharsets.UTF_8));

        for (String path : Arrays.asList("/../secret.txt", "../secret.txt", "/AVATAR/../../secret.txt",
                "\\AVATAR\\..\\..\\secret.txt", "/other.txt", "/AVATAR/../other.txt", secret.toString())) {
            fileService.deleteFile(path);
        }

        assertThat(Files.exists(secret)).isTrue();
        assertThat(Files.exists(other)).isTrue();
    }

    @Test
    void shouldKeepDirectoryWhenPathPointsAtFolder() throws IOException {
        Files.createDirectories(root.resolve("AVATAR").resolve("sub"));

        fileService.deleteFile("/AVATAR/sub");
        fileService.deleteFile("/AVATAR");
        fileService.deleteFile("/AVATAR/");

        assertThat(Files.isDirectory(root.resolve("AVATAR").resolve("sub"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("AVATAR"))).isTrue();
    }

    @Test
    void shouldIgnoreBlankPathsWhenDeleting() {
        fileService.deleteFile(null);
        fileService.deleteFile("  ");
        fileService.deleteFiles(Arrays.asList(null, "", "/AVATAR/missing.png"));

        assertThat(Files.exists(secret)).isTrue();
    }
}

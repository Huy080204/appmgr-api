package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.bundle.BundleFile;
import com.appmgr.api.dto.bundle.CheckVersionDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.mapper.VersionMapper;
import com.appmgr.api.model.Version;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.CategoryRepository;
import com.appmgr.api.repository.ChannelRepository;
import com.appmgr.api.repository.VersionRepository;
import com.appmgr.api.service.VersionFileService;
import com.appmgr.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleControllerTest {

    @Mock private VersionRepository versionRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private VersionFileService versionFileService;
    @Mock private UserServiceImpl userService;
    @Spy private VersionMapper versionMapper = Mappers.getMapper(VersionMapper.class);
    @InjectMocks private BundleController controller;

    @TempDir Path tempDir;

    @Test
    void shouldThrowBadRequestWhenApplicationNotFoundOnCheckVersion() {
        when(applicationRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> controller.checkVersion(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowBadRequestWhenCategoryNotFoundOnCheckVersion() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> controller.checkVersion(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowBadRequestWhenChannelNotFoundOnCheckVersion() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.existsById(2L)).thenReturn(true);
        when(channelRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> controller.checkVersion(1L, 2L, 3L))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnMappedFieldsWhenActiveVersionExists() {
        Version version = new Version();
        version.setId(10L);
        version.setType(BaseConstant.VERSION_TYPE_BUNDLE);
        version.setVersionCode(10);
        version.setVersionName("1.0");
        version.setMinVersion(5);
        version.setRequiredUpdate(true);
        version.setUrlBundle("https://example.com/bundle");
        stubParentsExist();
        when(versionRepository.findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(
                1L, 2L, 3L, BaseConstant.BUNDLE_VERSION_TYPES, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(version));

        ApiMessageDto<CheckVersionDto> result = controller.checkVersion(1L, 2L, 3L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getType()).isEqualTo(BaseConstant.VERSION_TYPE_BUNDLE);
        assertThat(result.getData().getVersionCode()).isEqualTo(10);
        assertThat(result.getData().getVersionName()).isEqualTo("1.0");
        assertThat(result.getData().getMinVersion()).isEqualTo(5);
        assertThat(result.getData().getRequiredUpdate()).isTrue();
        assertThat(result.getData().getUrlBundle()).isEqualTo("https://example.com/bundle");
        assertThat(result.getMessage()).isEqualTo("Check version success");
        verify(versionRepository).findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(
                1L, 2L, 3L, BaseConstant.BUNDLE_VERSION_TYPES, BaseConstant.STATUS_ACTIVE);
    }

    @Test
    void shouldReturnNullDataWhenNoActiveVersionExists() {
        stubParentsExist();
        when(versionRepository.findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(
                1L, 2L, 3L, BaseConstant.BUNDLE_VERSION_TYPES, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        ApiMessageDto<CheckVersionDto> result = controller.checkVersion(1L, 2L, 3L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData()).isNull();
        verify(versionRepository).findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(
                1L, 2L, 3L, BaseConstant.BUNDLE_VERSION_TYPES, BaseConstant.STATUS_ACTIVE);
    }

    @Test
    void shouldThrowNotFoundWhenNoActiveBundleVersionMatchesOnDownload() {
        when(versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                10L, 1L, 3L, BaseConstant.VERSION_TYPE_BUNDLE, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.downloadVersion(1L, 3L, 10L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
        verify(versionRepository).findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                10L, 1L, 3L, BaseConstant.VERSION_TYPE_BUNDLE, BaseConstant.STATUS_ACTIVE);
    }

    @Test
    void shouldReturnApkFileWithHeadersWhenBundleIsApk() throws IOException {
        Version version = stubActiveBundleVersion();
        Path file = writeFile("app_1.0_10.apk", "apk-content");
        when(versionFileService.resolveBundleFile(version)).thenReturn(
                new BundleFile(file, "app_1.0_10.apk", BaseConstant.BUNDLE_MEDIA_TYPE_APK));

        ResponseEntity<Resource> result = controller.downloadVersion(1L, 3L, 10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.readAllBytes(result.getBody().getFile().toPath())).isEqualTo(Files.readAllBytes(file));
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
                .isEqualTo("application/vnd.android.package-archive");
        assertThat(result.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(result.getHeaders().getContentDisposition().getFilename()).isEqualTo("app_1.0_10.apk");
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename*=UTF-8''app_1.0_10.apk");
    }

    @Test
    void shouldReturnTarGzFileWithHeadersWhenBundleIsTarGz() throws IOException {
        Version version = stubActiveBundleVersion();
        Path file = writeFile("app_1.0_10.tar.gz", "targz-content");
        when(versionFileService.resolveBundleFile(version)).thenReturn(
                new BundleFile(file, "app_1.0_10.tar.gz", BaseConstant.BUNDLE_MEDIA_TYPE_TAR_GZ));

        ResponseEntity<Resource> result = controller.downloadVersion(1L, 3L, 10L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Files.readAllBytes(result.getBody().getFile().toPath())).isEqualTo(Files.readAllBytes(file));
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/gzip");
        assertThat(result.getHeaders().getContentDisposition().getType()).isEqualTo("attachment");
        assertThat(result.getHeaders().getContentDisposition().getFilename()).isEqualTo("app_1.0_10.tar.gz");
        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename*=UTF-8''app_1.0_10.tar.gz");
    }

    @Test
    void shouldEmitUtf8FilenameStarWhenBundleNameIsNonAscii() throws IOException {
        Version version = stubActiveBundleVersion();
        String fileName = "tên_1.0_10.apk";
        Path file = writeFile(fileName, "apk-content");
        when(versionFileService.resolveBundleFile(version)).thenReturn(
                new BundleFile(file, fileName, BaseConstant.BUNDLE_MEDIA_TYPE_APK));

        ResponseEntity<Resource> result = controller.downloadVersion(1L, 3L, 10L);

        assertThat(result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment; filename*=UTF-8''")
                .contains("t%C3%AAn_1.0_10.apk");
    }

    @Test
    void shouldPercentEncodeLineBreaksWhenBundleNameHasControlCharacters() throws IOException {
        Version version = stubActiveBundleVersion();
        Path file = writeFile("app_1.0_10.apk", "apk-content");
        when(versionFileService.resolveBundleFile(version)).thenReturn(new BundleFile(
                file, "evil\r\nSet-Cookie: x=1_10.apk", BaseConstant.BUNDLE_MEDIA_TYPE_APK));

        ResponseEntity<Resource> result = controller.downloadVersion(1L, 3L, 10L);

        String header = result.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(header).doesNotContain("\r").doesNotContain("\n");
        assertThat(header).isEqualTo("attachment; filename*=UTF-8''evil%0D%0ASet-Cookie%3A%20x%3D1_10.apk");
    }

    @Test
    void shouldPropagateNotFoundWhenResolveBundleFileThrows() {
        Version version = stubActiveBundleVersion();
        when(versionFileService.resolveBundleFile(version))
                .thenThrow(new NotFoundException("Bundle file not found", ErrorCode.VERSION_ERROR_NOT_FOUND));

        assertThatThrownBy(() -> controller.downloadVersion(1L, 3L, 10L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    private void stubParentsExist() {
        when(applicationRepository.existsById(1L)).thenReturn(true);
        when(categoryRepository.existsById(2L)).thenReturn(true);
        when(channelRepository.existsById(3L)).thenReturn(true);
    }

    private Version stubActiveBundleVersion() {
        Version version = new Version();
        version.setId(10L);
        version.setType(BaseConstant.VERSION_TYPE_BUNDLE);
        when(versionRepository.findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(
                10L, 1L, 3L, BaseConstant.VERSION_TYPE_BUNDLE, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(version));
        return version;
    }

    private Path writeFile(String name, String content) throws IOException {
        return Files.write(tempDir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }
}

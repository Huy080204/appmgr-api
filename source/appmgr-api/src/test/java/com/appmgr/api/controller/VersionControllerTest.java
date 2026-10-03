package com.appmgr.api.controller;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.ResponseListDto;
import com.appmgr.api.dto.version.VersionDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.form.version.CreateVersionForm;
import com.appmgr.api.form.version.UpdateVersionForm;
import com.appmgr.api.mapper.ApplicationMapper;
import com.appmgr.api.mapper.CategoryMapper;
import com.appmgr.api.mapper.ChannelMapper;
import com.appmgr.api.mapper.ProjectMapper;
import com.appmgr.api.mapper.VersionMapper;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Category;
import com.appmgr.api.model.Channel;
import com.appmgr.api.model.Version;
import com.appmgr.api.model.criteria.VersionCriteria;
import com.appmgr.api.repository.ApplicationRepository;
import com.appmgr.api.repository.CategoryRepository;
import com.appmgr.api.repository.ChannelRepository;
import com.appmgr.api.repository.VersionRepository;
import com.appmgr.api.service.OtaService;
import com.appmgr.api.service.VersionFileService;
import com.appmgr.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionControllerTest {

    private static final Path VERSION_DIR = Paths.get("versions", "1", "prod", "5");

    @Mock private VersionRepository versionRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private VersionFileService versionFileService;
    @Mock private OtaService otaService;
    @Mock private UserServiceImpl userService;
    @Spy private VersionMapper versionMapper = Mappers.getMapper(VersionMapper.class);
    @InjectMocks private VersionController controller;

    @Test
    void shouldCreateBundleVersionWhenFormIsValid() {
        stubLookups();
        MockMultipartFile file = bundleFile();
        CreateVersionForm form = bundleForm(file);
        when(versionFileService.resolveBundleExtension("app.tar.gz")).thenReturn("tar.gz");
        stubSaveAssignsId();
        ArgumentCaptor<Version> versionCaptor = ArgumentCaptor.forClass(Version.class);

        ApiMessageDto<VersionDto> result = controller.create(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(5L);
        assertThat(result.getMessage()).isEqualTo("Create version success");
        InOrder order = inOrder(versionRepository, versionFileService);
        order.verify(versionRepository).save(versionCaptor.capture());
        order.verify(versionFileService).storeVersionBundle(any(Version.class), eq(file));
        order.verify(versionRepository).save(any(Version.class));
        verify(otaService, never()).publish(any(), any());
        Version saved = versionCaptor.getValue();
        assertThat(saved.getVersionName()).isEqualTo("1.0.0");
        assertThat(saved.getVersionCode()).isEqualTo(10);
        assertThat(saved.getMinVersion()).isEqualTo(5);
        assertThat(saved.getApplication().getId()).isEqualTo(1L);
        assertThat(saved.getCategory().getId()).isEqualTo(2L);
        assertThat(saved.getChannel().getId()).isEqualTo(3L);
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getUrlBundle()).isEqualTo("/v1/bundle/download-version?appId=1&channelId=3&versionId=5");
    }

    @Test
    void shouldThrowFileInvalidWhenBundleExtensionIsNotSupported() {
        stubLookups();
        CreateVersionForm form = bundleForm(new MockMultipartFile("file", "app.rar", null, new byte[]{1}));
        when(versionFileService.resolveBundleExtension("app.rar")).thenReturn(null);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldThrowFileInvalidWhenBundleFileIsEmpty() {
        stubLookups();
        CreateVersionForm form = bundleForm(new MockMultipartFile("file", "app.tar.gz", null, new byte[0]));
        when(versionFileService.resolveBundleExtension("app.tar.gz")).thenReturn("tar.gz");

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldThrowFileInvalidWhenBundleFileIsMissing() {
        stubLookups();
        CreateVersionForm form = bundleForm(null);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldThrowFieldRequiredWhenBundleMinVersionIsMissing() {
        stubLookups();
        CreateVersionForm form = bundleForm(bundleFile());
        form.setMinVersion(null);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldThrowBadRequestWhenBundleMinVersionIsNotLessThanVersionCode() {
        stubLookups();
        CreateVersionForm form = bundleForm(bundleFile());
        form.setMinVersion(10);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldCreateStoreVersionWithoutFileHandlingWhenFormIsValid() {
        stubLookups();
        stubSaveAssignsId();
        CreateVersionForm form = storeForm();
        ArgumentCaptor<Version> versionCaptor = ArgumentCaptor.forClass(Version.class);

        ApiMessageDto<VersionDto> result = controller.create(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(5L);
        assertThat(result.getMessage()).isEqualTo("Create version success");
        verify(versionRepository, times(1)).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getUrlBundle()).isEqualTo("https://store.example.com/app");
        assertThat(versionCaptor.getValue().getMinVersion()).isEqualTo(5);
        verify(versionFileService, never()).resolveVersionDir(any(), any(), any());
        verify(versionFileService, never()).storeVersionBundle(any(), any());
        verify(otaService, never()).publish(any(), any());
    }

    @Test
    void shouldThrowFieldRequiredWhenStoreUrlBundleIsMissing() {
        stubLookups();
        CreateVersionForm form = storeForm();
        form.setUrlBundle(" ");

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldIgnoreFileWhenStoreVersionHasFile() {
        stubLookups();
        stubSaveAssignsId();
        CreateVersionForm form = storeForm();
        form.setFile(bundleFile());

        ApiMessageDto<VersionDto> result = controller.create(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(5L);
        verify(versionRepository, times(1)).save(any(Version.class));
        verify(versionFileService, never()).storeVersionBundle(any(), any());
        verify(otaService, never()).publish(any(), any());
    }

    @Test
    void shouldThrowBadRequestWhenStoreMinVersionIsNotLessThanVersionCode() {
        stubLookups();
        CreateVersionForm form = storeForm();
        form.setMinVersion(11);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldCreateOtaVersionWithManifestIdWhenFormIsValid() {
        stubLookups();
        MockMultipartFile file = otaFile();
        CreateVersionForm form = otaForm(file);
        ArgumentCaptor<Version> versionCaptor = ArgumentCaptor.forClass(Version.class);
        when(otaService.publish(versionCaptor.capture(), any())).thenAnswer(publishAssignsId());

        ApiMessageDto<VersionDto> result = controller.create(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(5L);
        assertThat(result.getMessage()).isEqualTo("Create version success");
        verify(otaService).publish(any(Version.class), eq(file));
        verify(versionRepository, never()).save(any(Version.class));
        verify(versionFileService, never()).storeVersionBundle(any(), any());
        Version published = versionCaptor.getValue();
        assertThat(published.getMinVersion()).isNull();
        assertThat(published.getRuntimeVersion()).isEqualTo("1.0");
        assertThat(published.getChannel().getId()).isEqualTo(3L);
        assertThat(published.getTimestamp()).isNotNull();
    }

    @Test
    void shouldThrowFieldRequiredWhenOtaRuntimeVersionIsMissing() {
        stubLookups();
        CreateVersionForm form = otaForm(otaFile());
        form.setRuntimeVersion(null);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldIgnoreUrlBundleWhenOtaVersionHasUrlBundle() {
        stubLookups();
        CreateVersionForm form = otaForm(otaFile());
        form.setUrlBundle("https://store.example.com/app");
        ArgumentCaptor<Version> versionCaptor = ArgumentCaptor.forClass(Version.class);
        when(otaService.publish(versionCaptor.capture(), any())).thenAnswer(publishAssignsId());

        ApiMessageDto<VersionDto> result = controller.create(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(versionCaptor.getValue().getUrlBundle()).isNull();
    }

    @Test
    void shouldThrowFileInvalidWhenOtaFileIsMissing() {
        stubLookups();
        CreateVersionForm form = otaForm(null);

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldThrowFileInvalidWhenOtaFileIsEmpty() {
        stubLookups();
        CreateVersionForm form = otaForm(new MockMultipartFile("file", "ota.zip", null, new byte[0]));

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
        verify(versionRepository, never()).save(any(Version.class));
    }

    @Test
    void shouldThrowApplicationNotFoundWhenApplicationDoesNotExist() {
        CreateVersionForm form = bundleForm(bundleFile());
        when(applicationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowCategoryNotFoundWhenCategoryDoesNotExist() {
        CreateVersionForm form = bundleForm(bundleFile());
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application()));
        when(categoryRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CATEGORY_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowChannelNotFoundWhenChannelDoesNotExist() {
        CreateVersionForm form = bundleForm(bundleFile());
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application()));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category()));
        when(channelRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
    }

    @Test
    void shouldPropagateServiceErrorWhenBundlePublishFails() {
        stubLookups();
        MockMultipartFile file = bundleFile();
        CreateVersionForm form = bundleForm(file);
        when(versionFileService.resolveBundleExtension("app.tar.gz")).thenReturn("tar.gz");
        stubSaveAssignsId();
        doThrow(new BadRequestException("Store bundle failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED))
                .when(versionFileService).storeVersionBundle(any(), any());

        assertThatThrownBy(() -> controller.create(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
    }

    @Test
    void shouldIgnoreUrlBundleAndMinVersionAndApplyRuntimeVersionWhenUpdatingOtaVersion() {
        Version version = existingVersion(BaseConstant.VERSION_TYPE_OTA);
        version.setMinVersion(2);
        version.setUrlBundle("/old/url");
        version.setRuntimeVersion("1.0");
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));
        UpdateVersionForm form = updateForm();
        form.setRuntimeVersion("2.0");
        form.setUrlBundle("https://new.example.com");
        form.setMinVersion(9);

        ApiMessageDto<Void> result = controller.update(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update version success");
        verify(versionRepository).save(version);
        assertThat(version.getUrlBundle()).isEqualTo("/old/url");
        assertThat(version.getMinVersion()).isEqualTo(2);
        assertThat(version.getRuntimeVersion()).isEqualTo("2.0");
        assertThat(version.getVersionCode()).isEqualTo(20);
        assertThat(version.getVersionName()).isEqualTo("2.0.0");
        assertThat(version.getRequiredUpdate()).isFalse();
        assertThat(version.getVersionCode()).isEqualTo(20);
        assertThat(version.getVersionName()).isEqualTo("2.0.0");
    }

    @Test
    void shouldThrowFieldRequiredWhenUpdatingOtaVersionWithoutRuntimeVersion() {
        when(versionRepository.findById(5L)).thenReturn(Optional.of(existingVersion(BaseConstant.VERSION_TYPE_OTA)));
        UpdateVersionForm form = updateForm();

        assertThatThrownBy(() -> controller.update(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldUpdateStoreVersionWhenFormIsValid() {
        Version version = existingVersion(BaseConstant.VERSION_TYPE_STORE);
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));
        UpdateVersionForm form = updateForm();
        form.setMinVersion(9);
        form.setUrlBundle("https://store.example.com/new");
        form.setRuntimeVersion("9.9");

        ApiMessageDto<Void> result = controller.update(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Update version success");
        verify(versionRepository).save(version);
        assertThat(version.getUrlBundle()).isEqualTo("https://store.example.com/new");
        assertThat(version.getMinVersion()).isEqualTo(9);
        assertThat(version.getRuntimeVersion()).isNull();
    }

    @Test
    void shouldThrowFieldRequiredWhenUpdatingStoreVersionWithoutUrlBundle() {
        when(versionRepository.findById(5L)).thenReturn(Optional.of(existingVersion(BaseConstant.VERSION_TYPE_STORE)));
        UpdateVersionForm form = updateForm();
        form.setMinVersion(9);

        assertThatThrownBy(() -> controller.update(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldIgnoreUrlBundleWhenUpdatingBundleVersion() {
        Version version = existingVersion(BaseConstant.VERSION_TYPE_BUNDLE);
        version.setUrlBundle("/v1/bundle/download-version?appId=1&channelId=3&versionId=5");
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));
        UpdateVersionForm form = updateForm();
        form.setMinVersion(9);
        form.setUrlBundle("https://hacked.example.com");

        ApiMessageDto<Void> result = controller.update(form, new BeanPropertyBindingResult(form, "form"));

        assertThat(result.getResult()).isTrue();
        verify(versionRepository).save(version);
        assertThat(version.getUrlBundle()).isEqualTo("/v1/bundle/download-version?appId=1&channelId=3&versionId=5");
        assertThat(version.getMinVersion()).isEqualTo(9);
    }

    @Test
    void shouldThrowBadRequestWhenUpdatingBundleMinVersionNotLessThanVersionCode() {
        when(versionRepository.findById(5L)).thenReturn(Optional.of(existingVersion(BaseConstant.VERSION_TYPE_BUNDLE)));
        UpdateVersionForm form = updateForm();
        form.setMinVersion(20);

        assertThatThrownBy(() -> controller.update(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldThrowFieldRequiredWhenUpdatingBundleVersionWithoutMinVersion() {
        when(versionRepository.findById(5L)).thenReturn(Optional.of(existingVersion(BaseConstant.VERSION_TYPE_BUNDLE)));
        UpdateVersionForm form = updateForm();

        assertThatThrownBy(() -> controller.update(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", null);
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingMissingVersion() {
        UpdateVersionForm form = updateForm();
        when(versionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(form, new BeanPropertyBindingResult(form, "form")))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldRejectOtaCreateFormWhenRequiredUpdateIsMissing() {
        CreateVersionForm form = otaForm(otaFile());
        form.setRequiredUpdate(null);

        Set<ConstraintViolation<CreateVersionForm>> violations = validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("requiredUpdate");
    }

    @Test
    void shouldRejectOtaCreateFormWhenVersionCodeIsMissing() {
        CreateVersionForm form = otaForm(otaFile());
        form.setVersionCode(null);

        Set<ConstraintViolation<CreateVersionForm>> violations = validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("versionCode");
    }

    @Test
    void shouldRejectOtaCreateFormWhenVersionNameIsBlank() {
        CreateVersionForm form = otaForm(otaFile());
        form.setVersionName(" ");

        Set<ConstraintViolation<CreateVersionForm>> violations = validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("versionName");
    }

    @Test
    void shouldRejectUpdateFormWhenRequiredUpdateIsMissing() {
        UpdateVersionForm form = updateForm();
        form.setRequiredUpdate(null);

        Set<ConstraintViolation<UpdateVersionForm>> violations = validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("requiredUpdate");
    }

    @Test
    void shouldRejectUpdateFormWhenVersionCodeAndVersionNameAreMissing() {
        UpdateVersionForm form = updateForm();
        form.setVersionCode(null);
        form.setVersionName(null);

        Set<ConstraintViolation<UpdateVersionForm>> violations = validate(form);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("versionCode", "versionName");
    }

    @Test
    void shouldReturnVersionWhenIdExists() {
        wireNestedMappers();
        Version version = existingVersion(BaseConstant.VERSION_TYPE_BUNDLE);
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));

        ApiMessageDto<VersionDto> result = controller.get(5L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getId()).isEqualTo(5L);
        assertThat(result.getData().getType()).isEqualTo(BaseConstant.VERSION_TYPE_BUNDLE);
        assertThat(result.getData().getVersionCode()).isEqualTo(10);
        assertThat(result.getData().getVersionName()).isEqualTo("1.0.0");
        assertThat(result.getMessage()).isEqualTo("Get version success");
    }

    @Test
    void shouldThrowNotFoundWhenGettingMissingVersion() {
        when(versionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(5L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldDeleteVersionDirWhenDeletingBundleVersion() {
        Version version = existingVersion(BaseConstant.VERSION_TYPE_BUNDLE);
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));
        when(versionFileService.resolveVersionDir(1L, "prod", 5L)).thenReturn(VERSION_DIR);

        ApiMessageDto<Void> result = controller.delete(5L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Delete version success");
        InOrder order = inOrder(versionFileService, versionRepository);
        order.verify(versionFileService).deleteVersionDir(VERSION_DIR);
        order.verify(versionRepository).delete(version);
    }

    @Test
    void shouldNotTouchFilesWhenDeletingStoreVersion() {
        Version version = existingVersion(BaseConstant.VERSION_TYPE_STORE);
        when(versionRepository.findById(5L)).thenReturn(Optional.of(version));

        ApiMessageDto<Void> result = controller.delete(5L);

        assertThat(result.getResult()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Delete version success");
        verify(versionRepository).delete(version);
        verify(versionFileService, never()).resolveVersionDir(any(), any(), any());
        verify(versionFileService, never()).deleteVersionDir(any());
    }

    @Test
    void shouldThrowNotFoundWhenDeletingMissingVersion() {
        when(versionRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.delete(5L))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnPagedVersionsWhenListing() {
        wireNestedMappers();
        Version version = existingVersion(BaseConstant.VERSION_TYPE_BUNDLE);
        Page<Version> page = new PageImpl<>(List.of(version), PageRequest.of(0, 10), 1);
        when(versionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        ApiMessageDto<ResponseListDto<List<VersionDto>>> result =
                controller.list(new VersionCriteria(), PageRequest.of(0, 10));

        assertThat(result.getResult()).isTrue();
        assertThat(result.getData().getContent())
                .extracting(VersionDto::getId, VersionDto::getVersionName)
                .containsExactly(tuple(5L, "1.0.0"));
        assertThat(result.getData().getTotalPages()).isEqualTo(1);
        assertThat(result.getData().getTotalElements()).isEqualTo(1L);
        assertThat(result.getMessage()).isEqualTo("Get list success");
    }

    private static <T> Set<ConstraintViolation<T>> validate(T form) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(form);
        }
    }

    private void wireNestedMappers() {
        ApplicationMapper applicationMapper = Mappers.getMapper(ApplicationMapper.class);
        ReflectionTestUtils.setField(applicationMapper, "projectMapper", Mappers.getMapper(ProjectMapper.class));
        ReflectionTestUtils.setField(versionMapper, "applicationMapper", applicationMapper);
        ReflectionTestUtils.setField(versionMapper, "categoryMapper", Mappers.getMapper(CategoryMapper.class));
        ReflectionTestUtils.setField(versionMapper, "channelMapper", Mappers.getMapper(ChannelMapper.class));
    }

    private void stubLookups() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application()));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category()));
        when(channelRepository.findById(3L)).thenReturn(Optional.of(channel()));
    }

    private void stubSaveAssignsId() {
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> {
            Version version = invocation.getArgument(0);
            version.setId(5L);
            return version;
        });
    }

    private static Answer<Version> publishAssignsId() {
        return invocation -> {
            Version version = invocation.getArgument(0);
            version.setId(5L);
            return version;
        };
    }

    private Application application() {
        Application application = new Application();
        application.setId(1L);
        application.setName("MyApp");
        return application;
    }

    private Category category() {
        Category category = new Category();
        category.setId(2L);
        return category;
    }

    private Channel channel() {
        Channel channel = new Channel();
        channel.setId(3L);
        channel.setName("prod");
        return channel;
    }

    private Version existingVersion(Integer type) {
        Version version = new Version();
        version.setId(5L);
        version.setType(type);
        version.setVersionCode(10);
        version.setVersionName("1.0.0");
        version.setApplication(application());
        version.setChannel(channel());
        version.setCategory(category());
        return version;
    }

    private MockMultipartFile bundleFile() {
        return new MockMultipartFile("file", "app.tar.gz", null, new byte[]{1, 2, 3});
    }

    private MockMultipartFile otaFile() {
        return new MockMultipartFile("file", "ota.zip", null, new byte[]{1, 2, 3});
    }

    private CreateVersionForm baseCreateForm(Integer type) {
        CreateVersionForm form = new CreateVersionForm();
        form.setApplicationId(1L);
        form.setCategoryId(2L);
        form.setChannelId(3L);
        form.setType(type);
        form.setRequiredUpdate(true);
        return form;
    }

    private CreateVersionForm bundleForm(MockMultipartFile file) {
        CreateVersionForm form = baseCreateForm(BaseConstant.VERSION_TYPE_BUNDLE);
        form.setVersionCode(10);
        form.setVersionName("1.0.0");
        form.setMinVersion(5);
        form.setFile(file);
        return form;
    }

    private CreateVersionForm storeForm() {
        CreateVersionForm form = baseCreateForm(BaseConstant.VERSION_TYPE_STORE);
        form.setVersionCode(10);
        form.setVersionName("1.0.0");
        form.setMinVersion(5);
        form.setUrlBundle("https://store.example.com/app");
        return form;
    }

    private CreateVersionForm otaForm(MockMultipartFile file) {
        CreateVersionForm form = baseCreateForm(BaseConstant.VERSION_TYPE_OTA);
        form.setVersionCode(10);
        form.setVersionName("1.0.0");
        form.setMinVersion(3);
        form.setRuntimeVersion("1.0");
        form.setFile(file);
        return form;
    }

    private UpdateVersionForm updateForm() {
        UpdateVersionForm form = new UpdateVersionForm();
        form.setId(5L);
        form.setVersionCode(20);
        form.setVersionName("2.0.0");
        form.setRequiredUpdate(false);
        return form;
    }
}

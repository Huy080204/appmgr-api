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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/v1/version")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class VersionController extends ABasicController {
    @Autowired
    private VersionRepository versionRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private VersionMapper versionMapper;

    @Autowired
    private VersionFileService versionFileService;

    @Autowired
    private OtaService otaService;

    @GetMapping(value = "/get/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('VER_V')")
    public ApiMessageDto<VersionDto> get(@PathVariable Long id) {
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found version!", ErrorCode.VERSION_ERROR_NOT_FOUND));
        return makeSuccessResponse(versionMapper.fromEntityToDto(version), "Get version success");
    }

    @GetMapping(value = "/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('VER_L')")
    public ApiMessageDto<ResponseListDto<List<VersionDto>>> list(VersionCriteria versionCriteria, @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Version> page = versionRepository.findAll(versionCriteria.getCriteria(), pageable);
        ResponseListDto<List<VersionDto>> responseListDto =
                makeResponseListDto(page, versionMapper::fromEntitiesToDtoList);
        return makeSuccessResponse(responseListDto, "Get list success");
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('VER_C')")
    @Transactional
    public ApiMessageDto<VersionDto> create(@Valid CreateVersionForm createVersionForm, BindingResult bindingResult) {
        Application application = applicationRepository.findById(createVersionForm.getApplicationId())
                .orElseThrow(() -> new BadRequestException("Not found application!", ErrorCode.APPLICATION_ERROR_NOT_FOUND));
        Category category = categoryRepository.findById(createVersionForm.getCategoryId())
                .orElseThrow(() -> new BadRequestException("Not found category!", ErrorCode.CATEGORY_ERROR_NOT_FOUND));
        Channel channel = channelRepository.findById(createVersionForm.getChannelId())
                .orElseThrow(() -> new BadRequestException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND));
        validateCreateForm(createVersionForm);

        Version version = versionMapper.fromCreateForm(createVersionForm);
        version.setApplication(application);
        version.setCategory(category);
        version.setChannel(channel);
        version.setTimestamp(Instant.now().toEpochMilli());
        if (BaseConstant.VERSION_TYPE_STORE.equals(version.getType())) {
            version.setMinVersion(createVersionForm.getMinVersion());
            version.setUrlBundle(createVersionForm.getUrlBundle());
            versionRepository.save(version);
        } else if (BaseConstant.VERSION_TYPE_BUNDLE.equals(version.getType())) {
            version.setMinVersion(createVersionForm.getMinVersion());
            versionRepository.save(version);
            versionFileService.storeVersionBundle(version, createVersionForm.getFile());
            version.setUrlBundle(buildBundleUrl(version));
            versionRepository.save(version);
        } else {
            version = otaService.publish(version, createVersionForm.getFile());
        }
        return makeSuccessResponse(versionMapper.fromEntityToVersionIdDto(version), "Create version success");
    }

    @PutMapping(value = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('VER_U')")
    @Transactional
    public ApiMessageDto<Void> update(@Valid @RequestBody UpdateVersionForm updateVersionForm, BindingResult bindingResult) {
        Version version = versionRepository.findById(updateVersionForm.getId())
                .orElseThrow(() -> new NotFoundException("Not found version!", ErrorCode.VERSION_ERROR_NOT_FOUND));
        validateUpdateForm(updateVersionForm, version.getType());

        versionMapper.updateFromUpdateForm(updateVersionForm, version);
        if (BaseConstant.VERSION_TYPE_STORE.equals(version.getType())) {
            version.setMinVersion(updateVersionForm.getMinVersion());
            version.setUrlBundle(updateVersionForm.getUrlBundle());
        } else if (BaseConstant.VERSION_TYPE_BUNDLE.equals(version.getType())) {
            version.setMinVersion(updateVersionForm.getMinVersion());
        } else {
            version.setRuntimeVersion(updateVersionForm.getRuntimeVersion());
        }
        versionRepository.save(version);
        return makeSuccessResponse("Update version success");
    }

    @DeleteMapping(value = "/delete/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('VER_D')")
    @Transactional
    public ApiMessageDto<Void> delete(@PathVariable Long id) {
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Not found version!", ErrorCode.VERSION_ERROR_NOT_FOUND));
        if (!BaseConstant.VERSION_TYPE_STORE.equals(version.getType())) {
            versionFileService.deleteVersionDir(versionFileService.resolveVersionDir(
                    version.getApplication().getId(), version.getChannel().getName(), version.getId()));
        }
        versionRepository.delete(version);
        return makeSuccessResponse("Delete version success");
    }

    private String buildBundleUrl(Version version) {
        return UriComponentsBuilder.fromPath(BaseConstant.VERSION_DOWNLOAD_PATH)
                .queryParam("appId", version.getApplication().getId())
                .queryParam("channelId", version.getChannel().getId())
                .queryParam("versionId", version.getId())
                .toUriString();
    }

    private void validateCreateForm(CreateVersionForm form) {
        MultipartFile file = form.getFile();
        if (BaseConstant.VERSION_TYPE_BUNDLE.equals(form.getType())) {
            validateMinVersion(form.getMinVersion(), form.getVersionCode());
            if (file == null || versionFileService.resolveBundleExtension(file.getOriginalFilename()) == null
                    || file.isEmpty()) {
                throw new BadRequestException("Bundle file is invalid");
            }
        } else if (BaseConstant.VERSION_TYPE_STORE.equals(form.getType())) {
            if (!StringUtils.hasText(form.getUrlBundle())) {
                throw new BadRequestException("urlBundle is required");
            }
            validateMinVersion(form.getMinVersion(), form.getVersionCode());
        } else {
            if (!StringUtils.hasText(form.getRuntimeVersion())) {
                throw new BadRequestException("runtimeVersion is required");
            }
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("OTA file is required");
            }
        }
    }

    private void validateUpdateForm(UpdateVersionForm form, Integer type) {
        if (BaseConstant.VERSION_TYPE_OTA.equals(type)) {
            if (!StringUtils.hasText(form.getRuntimeVersion())) {
                throw new BadRequestException("runtimeVersion is required");
            }
            return;
        }
        validateMinVersion(form.getMinVersion(), form.getVersionCode());
        if (BaseConstant.VERSION_TYPE_STORE.equals(type) && !StringUtils.hasText(form.getUrlBundle())) {
            throw new BadRequestException("urlBundle is required");
        }
    }

    private void validateMinVersion(Integer minVersion, Integer versionCode) {
        if (minVersion == null) {
            throw new BadRequestException("minVersion is required");
        }
        if (minVersion >= versionCode) {
            throw new BadRequestException("minVersion must be less than versionCode");
        }
    }
}

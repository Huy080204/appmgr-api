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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/v1/bundle")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class BundleController extends ABasicController {
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

    @GetMapping(value = "/check-version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiMessageDto<CheckVersionDto> checkVersion(@RequestParam Long appId, @RequestParam Long categoryId, @RequestParam Long channelId) {
        if (!applicationRepository.existsById(appId)) {
            throw new BadRequestException("Not found application!", ErrorCode.APPLICATION_ERROR_NOT_FOUND);
        }
        if (!categoryRepository.existsById(categoryId)) {
            throw new BadRequestException("Not found category!", ErrorCode.CATEGORY_ERROR_NOT_FOUND);
        }
        if (!channelRepository.existsById(channelId)) {
            throw new BadRequestException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
        }

        CheckVersionDto checkVersionDto = versionRepository
                .findFirstByApplicationIdAndCategoryIdAndChannelIdAndTypeInAndStatusOrderByTimestampDesc(
                        appId, categoryId, channelId,
                        BaseConstant.BUNDLE_VERSION_TYPES,
                        BaseConstant.STATUS_ACTIVE)
                .map(versionMapper::fromEntityToCheckVersionDto)
                .orElse(null);
        return makeSuccessResponse(checkVersionDto, "Check version success");
    }

    @GetMapping(value = "/download-version")
    public ResponseEntity<Resource> downloadVersion(@RequestParam Long appId, @RequestParam Long channelId, @RequestParam Long versionId) {
        Version version = versionRepository
                .findByIdAndApplicationIdAndChannelIdAndTypeAndStatus(versionId, appId, channelId,
                        BaseConstant.VERSION_TYPE_BUNDLE, BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("Not found version!", ErrorCode.VERSION_ERROR_NOT_FOUND));

        BundleFile file = versionFileService.resolveBundleFile(version);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.getPath()));
    }
}

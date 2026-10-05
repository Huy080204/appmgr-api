package com.appmgr.api.controller;

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
import com.appmgr.api.service.OtaSigningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ota")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@Slf4j
public class OtaController extends ABasicController {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private VersionRepository versionRepository;

    @Autowired
    private OtaSigningService otaSigningService;

    @GetMapping(value = "/manifest")
    public ResponseEntity<byte[]> manifest(@RequestHeader HttpHeaders headers) {
        Long appId = parseId(headers, BaseConstant.OTA_HEADER_APP_ID);
        Long categoryId = parseId(headers, BaseConstant.OTA_HEADER_CATEGORY_ID);
        String channelName = requireHeader(headers, BaseConstant.OTA_HEADER_CHANNEL_NAME);
        String platform = requireHeader(headers, BaseConstant.OTA_HEADER_PLATFORM);
        if (!BaseConstant.OTA_PLATFORM_IOS.equals(platform) && !BaseConstant.OTA_PLATFORM_ANDROID.equals(platform)) {
            throw new BadRequestException("Invalid header " + BaseConstant.OTA_HEADER_PLATFORM);
        }
        String runtimeVersion = requireHeader(headers, BaseConstant.OTA_HEADER_RUNTIME_VERSION);
        String currentUpdateId = headers.getFirst(BaseConstant.OTA_HEADER_CURRENT_UPDATE_ID);

        Channel channel = channelRepository.findByName(channelName)
                .orElseThrow(() -> new NotFoundException("Not found channel!", ErrorCode.CHANNEL_ERROR_NOT_FOUND));
        Version version = versionRepository
                .findFirstByApplicationIdAndCategoryIdAndChannelIdAndRuntimeVersionAndTypeAndStatusOrderByTimestampDesc(
                        appId, categoryId, channel.getId(), runtimeVersion,
                        BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE)
                .orElseThrow(() -> new NotFoundException("Not found version!", ErrorCode.VERSION_ERROR_NOT_FOUND));

        OtaPart part;
        if (currentUpdateId != null && currentUpdateId.equals(version.getManifestId())) {
            part = otaSigningService.buildNoUpdateDirective();
        } else {
            part = otaSigningService.buildManifest(version, appId, channel.getName(), platform);
        }
        return buildMultipartResponse(part);
    }

    @GetMapping(value = "/assets/{appId}/{channelName}/{versionId}/**")
    public ResponseEntity<Resource> assets(@PathVariable String appId, @PathVariable String channelName,
                                           @PathVariable String versionId, HttpServletRequest request) {
        String pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        String path = String.valueOf(request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE));
        String filePath = PATH_MATCHER.extractPathWithinPattern(pattern, path);
        OtaAsset otaAsset = otaSigningService.resolveAsset(appId, channelName, versionId, filePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(otaAsset.getMediaType()))
                .body(otaAsset.getResource());
    }

    private Long parseId(HttpHeaders headers, String name) {
        String value = requireHeader(headers, name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid header " + name);
        }
    }

    private String requireHeader(HttpHeaders headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.trim().isEmpty()) {
            throw new BadRequestException("Missing header " + name);
        }
        return value.trim();
    }

    private ResponseEntity<byte[]> buildMultipartResponse(OtaPart part) {
        String boundary = UUID.randomUUID().toString();
        String body = buildMultipartBody(part, boundary);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, BaseConstant.OTA_MEDIA_TYPE_MULTIPART + boundary)
                .header(BaseConstant.OTA_HEADER_PROTOCOL_VERSION, BaseConstant.OTA_PROTOCOL_VERSION)
                .header(BaseConstant.OTA_HEADER_SFV_VERSION, BaseConstant.OTA_SFV_VERSION)
                .header(HttpHeaders.CACHE_CONTROL, BaseConstant.OTA_CACHE_CONTROL)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private String buildMultipartBody(OtaPart part, String boundary) {
        String signatureLine = part.getSignatureHeader() == null ? ""
                : BaseConstant.OTA_HEADER_SIGNATURE + ": " + part.getSignatureHeader() + BaseConstant.OTA_CRLF;
        return BaseConstant.OTA_BOUNDARY_PREFIX + boundary + BaseConstant.OTA_CRLF
                + BaseConstant.OTA_PART_DISPOSITION_PREFIX + part.getName() + "\"" + BaseConstant.OTA_CRLF
                + BaseConstant.OTA_PART_CONTENT_TYPE + BaseConstant.OTA_CRLF
                + signatureLine
                + BaseConstant.OTA_CRLF
                + part.getJson() + BaseConstant.OTA_CRLF
                + BaseConstant.OTA_BOUNDARY_PREFIX + boundary + BaseConstant.OTA_BOUNDARY_PREFIX + BaseConstant.OTA_CRLF;
    }
}

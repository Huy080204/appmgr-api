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
import com.appmgr.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.NestedServletException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OtaControllerTest {

    private static final String BOUNDARY_PREFIX = "multipart/mixed; boundary=";
    private static final String CRLF = "\r\n";

    @Mock private ChannelRepository channelRepository;
    @Mock private VersionRepository versionRepository;
    @Mock private OtaSigningService otaSigningService;
    @Mock private UserServiceImpl userService;                 // ABasicController @Autowired collaborator
    @InjectMocks private OtaController controller;

    @Test
    void shouldThrowBadRequestWhenAppIdHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("x-app-id");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenCategoryIdHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("x-category-id");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenChannelNameHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("expo-channel-name");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldNotQueryChannelWhenChannelNameHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("expo-channel-name");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(channelRepository);
    }

    @Test
    void shouldThrowBadRequestWhenPlatformHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("expo-platform");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenRuntimeVersionHeaderMissing() {
        HttpHeaders headers = validHeaders();
        headers.remove("expo-runtime-version");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenChannelNameHeaderBlank() {
        HttpHeaders headers = validHeaders();
        headers.set("expo-channel-name", "   ");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenAppIdHeaderNotNumeric() {
        HttpHeaders headers = validHeaders();
        headers.set("x-app-id", "abc");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenPlatformIsWeb() {
        HttpHeaders headers = validHeaders();
        headers.set("expo-platform", "web");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowBadRequestWhenOnlyLegacyAppIdHeaderGiven() {
        HttpHeaders headers = validHeaders();
        headers.remove("x-app-id");
        headers.set("appId", "1");

        assertThatThrownBy(() -> controller.manifest(headers)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void shouldThrowNotFoundWhenChannelNameUnknown() {
        when(channelRepository.findByName("production")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.manifest(validHeaders()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_NOT_FOUND);
    }

    @Test
    void shouldThrowNotFoundWhenNoOtaVersionForChannel() {
        when(channelRepository.findByName("production")).thenReturn(Optional.of(channel(5L, "production")));
        when(versionRepository.findFirstByApplicationIdAndCategoryIdAndChannelIdAndRuntimeVersionAndTypeAndStatusOrderByTimestampDesc(
                1L, 2L, 5L, "1.0.0", BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.manifest(validHeaders()))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    @Test
    void shouldReturnNoUpdateDirectiveWhenCurrentUpdateIdEqualsManifestId() {
        stubLatestVersion("manifest-1");
        String json = "{\"type\":\"noUpdateAvailable\"}";
        HttpHeaders headers = validHeaders();
        headers.set("expo-current-update-id", "manifest-1");
        when(otaSigningService.buildNoUpdateDirective())
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_DIRECTIVE, json, null));

        ResponseEntity<byte[]> response = controller.manifest(headers);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        String body = bodyOf(response);
        assertThat(body).contains("name=\"directive\"");
        assertThat(body).doesNotContain("name=\"manifest\"");
        assertThat(body).contains(json);
        verify(otaSigningService).buildNoUpdateDirective();
        verify(otaSigningService, never()).buildManifest(any(Version.class), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldBuildManifestWhenCurrentUpdateIdDiffers() {
        Version version = stubLatestVersion("manifest-1");
        HttpHeaders headers = validHeaders();
        headers.set("expo-current-update-id", "manifest-0");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> response = controller.manifest(headers);

        assertThat(bodyOf(response)).contains("name=\"manifest\"");
        verify(otaSigningService).buildManifest(version, 1L, "production", "ios");
        verify(otaSigningService, never()).buildNoUpdateDirective();
    }

    @Test
    void shouldBuildManifestWhenCurrentUpdateIdAbsent() {
        Version version = stubLatestVersion("manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> response = controller.manifest(validHeaders());

        assertThat(bodyOf(response)).contains("name=\"manifest\"");
        verify(otaSigningService).buildManifest(version, 1L, "production", "ios");
        verify(otaSigningService, never()).buildNoUpdateDirective();
    }

    @Test
    void shouldBuildManifestWhenCurrentUpdateIdPresentButManifestIdNull() {
        Version version = stubLatestVersion(null);
        HttpHeaders headers = validHeaders();
        headers.set("expo-current-update-id", "manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> response = controller.manifest(headers);

        assertThat(bodyOf(response)).contains("name=\"manifest\"");
        verify(otaSigningService).buildManifest(version, 1L, "production", "ios");
        verify(otaSigningService, never()).buildNoUpdateDirective();
    }

    @Test
    void shouldSetProtocolHeadersAndMatchingBoundaryWhenManifestBuilt() {
        Version version = stubLatestVersion("manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> response = controller.manifest(validHeaders());

        HttpHeaders out = response.getHeaders();
        assertThat(out.getFirst("expo-protocol-version")).isEqualTo("1");
        assertThat(out.getFirst("expo-sfv-version")).isEqualTo("0");
        assertThat(out.getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, max-age=0");
        String contentType = out.getFirst(HttpHeaders.CONTENT_TYPE);
        assertThat(contentType).startsWith(BOUNDARY_PREFIX);
        String boundary = contentType.substring(BOUNDARY_PREFIX.length());
        assertThat(boundary).isNotBlank();
        assertThat(bodyOf(response)).startsWith("--" + boundary + CRLF);
    }

    @Test
    void shouldWriteManifestPartOnceAndCloseWithBoundaryWhenManifestBuilt() {
        Version version = stubLatestVersion("manifest-1");
        String json = "{\"id\":\"manifest-1\"}";
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, json, null));

        ResponseEntity<byte[]> response = controller.manifest(validHeaders());

        String boundary = boundaryOf(response);
        String body = bodyOf(response);
        assertThat(body).contains("Content-Disposition: form-data; name=\"manifest\"");
        assertThat(body).doesNotContain("name=\"directive\"");
        assertThat(countOccurrences(body, json)).isEqualTo(1);
        assertThat(body).endsWith("--" + boundary + "--" + CRLF);
    }

    @Test
    void shouldWriteDirectivePartWhenNoUpdateDirectiveBuilt() {
        stubLatestVersion("manifest-1");
        String json = "{\"type\":\"noUpdateAvailable\"}";
        HttpHeaders headers = validHeaders();
        headers.set("expo-current-update-id", "manifest-1");
        when(otaSigningService.buildNoUpdateDirective())
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_DIRECTIVE, json, null));

        ResponseEntity<byte[]> response = controller.manifest(headers);

        String boundary = boundaryOf(response);
        String body = bodyOf(response);
        assertThat(body).contains("Content-Disposition: form-data; name=\"directive\"");
        assertThat(body).doesNotContain("name=\"manifest\"");
        assertThat(countOccurrences(body, json)).isEqualTo(1);
        assertThat(body).endsWith("--" + boundary + "--" + CRLF);
    }

    @Test
    void shouldIncludeSignatureLineWhenSignatureHeaderPresent() {
        Version version = stubLatestVersion("manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}",
                        "sig=:abc:, keyid=\"main\""));

        ResponseEntity<byte[]> response = controller.manifest(validHeaders());

        assertThat(bodyOf(response)).contains("expo-signature: sig=:abc:, keyid=\"main\"" + CRLF);
    }

    @Test
    void shouldOmitSignatureLineWhenSignatureHeaderNull() {
        Version version = stubLatestVersion("manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> response = controller.manifest(validHeaders());

        assertThat(bodyOf(response)).doesNotContain("expo-signature:");
    }

    @Test
    void shouldUseDifferentBoundaryOnEachCall() {
        Version version = stubLatestVersion("manifest-1");
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, "{\"id\":\"manifest-1\"}", null));

        ResponseEntity<byte[]> first = controller.manifest(validHeaders());
        ResponseEntity<byte[]> second = controller.manifest(validHeaders());

        assertThat(boundaryOf(first)).isNotEqualTo(boundaryOf(second));
    }

    @Test
    void shouldReturnAssetWithMediaTypeAndNoContentDispositionWhenAssetResolved() {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(otaSigningService.resolveAsset("10", "prod", "555", "assets/def"))
                .thenReturn(new OtaAsset(resource, "image/png"));

        ResponseEntity<Resource> response = controller.assets("10", "prod", "555",
                assetRequest("/v1/ota/assets/10/prod/555/assets/def"));

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().containsKey(HttpHeaders.CONTENT_DISPOSITION)).isFalse();
        assertThat(response.getBody()).isSameAs(resource);
    }

    @Test
    void shouldPropagateNotFoundWhenAssetCannotBeResolved() {
        when(otaSigningService.resolveAsset("10", "prod", "555", "missing"))
                .thenThrow(new NotFoundException("Not found asset!", ErrorCode.VERSION_ERROR_NOT_FOUND));
        MockHttpServletRequest request = assetRequest("/v1/ota/assets/10/prod/555/missing");

        assertThatThrownBy(() -> controller.assets("10", "prod", "555", request))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND);
    }

    // ---------------------------------------------------------------- real MVC wiring (mirrors WebMvcConfig: no String converter)

    @Test
    void shouldServeMultipartManifestThroughMvcWhenOnlyByteArrayJacksonAndResourceConvertersRegistered() throws Exception {
        Version version = stubLatestVersion("manifest-1");
        String json = "{\"id\":\"manifest-1\"}";
        when(otaSigningService.buildManifest(version, 1L, "production", "ios"))
                .thenReturn(new OtaPart(BaseConstant.OTA_PART_MANIFEST, json, null));

        MvcResult result = buildMockMvc().perform(get("/v1/ota/manifest")
                        .header("x-app-id", "1")
                        .header("x-category-id", "2")
                        .header("expo-channel-name", "production")
                        .header("expo-platform", "ios")
                        .header("expo-runtime-version", "1.0.0"))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE);
        assertThat(contentType).startsWith(BOUNDARY_PREFIX);
        String boundary = contentType.substring(BOUNDARY_PREFIX.length());
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(body).contains(json);
        assertThat(body).endsWith("--" + boundary + "--" + CRLF);
    }

    @Test
    void shouldServeAssetBytesThroughMvcWhenNestedFilePathRequested() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3, 4};
        when(otaSigningService.resolveAsset("10", "prod", "555", "_expo/static/js/android/index-abc.hbc"))
                .thenReturn(new OtaAsset(new ByteArrayResource(bytes), "application/javascript"));

        MvcResult result = buildMockMvc()
                .perform(get("/v1/ota/assets/10/prod/555/_expo/static/js/android/index-abc.hbc"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(result.getResponse().getContentType()).startsWith("application/javascript");
    }

    @Test
    void shouldPassSingleSegmentFilePathToServiceThroughMvcWhenFileIsDirectlyUnderVersion() throws Exception {
        byte[] bytes = new byte[]{9, 8};
        when(otaSigningService.resolveAsset("10", "prod", "555", "assets/def"))
                .thenReturn(new OtaAsset(new ByteArrayResource(bytes), "image/png"));

        MvcResult result = buildMockMvc().perform(get("/v1/ota/assets/10/prod/555/assets/def"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(result.getResponse().getContentType()).startsWith("image/png");
    }

    @Test
    void shouldSurfaceNotFoundThroughMvcWhenServiceCannotResolveAsset() {
        when(otaSigningService.resolveAsset("10", "prod", "555", "assets/def"))
                .thenThrow(new NotFoundException("Asset not found", ErrorCode.VERSION_ERROR_NOT_FOUND));
        MockMvc mockMvc = buildMockMvc();

        assertThatThrownBy(() -> mockMvc.perform(get("/v1/ota/assets/10/prod/555/assets/def")))
                .isInstanceOf(NestedServletException.class)
                .satisfies(ex -> assertThat(ex.getCause())
                        .isInstanceOf(NotFoundException.class)
                        .hasFieldOrPropertyWithValue("code", ErrorCode.VERSION_ERROR_NOT_FOUND));
    }

    private MockHttpServletRequest assetRequest(String requestPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestPath);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/v1/ota/assets/{appId}/{channelName}/{versionId}/**");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, requestPath);
        return request;
    }

    private MockMvc buildMockMvc() {
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new ByteArrayHttpMessageConverter(),
                        new MappingJackson2HttpMessageConverter(),
                        new ResourceHttpMessageConverter())
                .build();
    }

    private HttpHeaders validHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-app-id", "1");
        headers.set("x-category-id", "2");
        headers.set("expo-channel-name", "production");
        headers.set("expo-platform", "ios");
        headers.set("expo-runtime-version", "1.0.0");
        return headers;
    }

    private Channel channel(Long id, String name) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setName(name);
        return channel;
    }

    private Version stubLatestVersion(String manifestId) {
        Version version = new Version();
        version.setManifestId(manifestId);
        when(channelRepository.findByName("production")).thenReturn(Optional.of(channel(5L, "production")));
        when(versionRepository.findFirstByApplicationIdAndCategoryIdAndChannelIdAndRuntimeVersionAndTypeAndStatusOrderByTimestampDesc(
                1L, 2L, 5L, "1.0.0", BaseConstant.VERSION_TYPE_OTA, BaseConstant.STATUS_ACTIVE))
                .thenReturn(Optional.of(version));
        return version;
    }

    private String bodyOf(ResponseEntity<byte[]> response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private String boundaryOf(ResponseEntity<byte[]> response) {
        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        return contentType.substring(BOUNDARY_PREFIX.length());
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}

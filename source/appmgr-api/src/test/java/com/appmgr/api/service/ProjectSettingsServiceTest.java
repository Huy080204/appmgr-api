package com.appmgr.api.service;

import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.project.ProjectKeyDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.model.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link ProjectSettingsService#findKey(Project, String)}.
 */
@ExtendWith(MockitoExtension.class)
class ProjectSettingsServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProjectSettingsService service;

    private Project projectWithSettings(String settings) {
        Project project = new Project();
        project.setId(1L);
        project.setSettings(settings);
        return project;
    }

    @Test
    void shouldReturnMatchingKeyWhenKeyIdExists() {
        Project project = projectWithSettings("{\"keys\":[{\"keyId\":\"k1\",\"privateKey\":\"priv1\",\"publicKey\":\"pub1\"},"
                + "{\"keyId\":\"k2\",\"privateKey\":\"priv2\",\"publicKey\":\"pub2\"}]}");

        ProjectKeyDto result = service.findKey(project, "k2");

        assertThat(result).isNotNull();
        assertThat(result.getKeyId()).isEqualTo("k2");
        assertThat(result.getPublicKey()).isEqualTo("pub2");
    }

    @Test
    void shouldReturnFirstKeyWhenKeyIdIsDuplicated() {
        Project project = projectWithSettings("{\"keys\":[{\"keyId\":\"k1\",\"privateKey\":\"a\",\"publicKey\":\"first\"},"
                + "{\"keyId\":\"k1\",\"privateKey\":\"b\",\"publicKey\":\"second\"}]}");

        ProjectKeyDto result = service.findKey(project, "k1");

        assertThat(result).isNotNull();
        assertThat(result.getPublicKey()).isEqualTo("first");
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenSettingsIsNull() {
        Project project = projectWithSettings(null);

        assertThatThrownBy(() -> service.findKey(project, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenSettingsIsBlank() {
        Project project = projectWithSettings("   ");

        assertThatThrownBy(() -> service.findKey(project, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenSettingsIsMalformedJson() {
        Project project = projectWithSettings("{not json");

        assertThatThrownBy(() -> service.findKey(project, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenNoKeyMatches() {
        Project project = projectWithSettings("{\"keys\":[{\"keyId\":\"k1\",\"privateKey\":\"a\",\"publicKey\":\"pub1\"}]}");

        assertThatThrownBy(() -> service.findKey(project, "other"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldSkipNullElementWhenKeysContainsNull() {
        Project project = projectWithSettings("{\"keys\":[null,{\"keyId\":\"k1\",\"privateKey\":\"a\",\"publicKey\":\"pub1\"}]}");

        ProjectKeyDto result = service.findKey(project, "k1");

        assertThat(result.getPublicKey()).isEqualTo("pub1");
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenKeysOnlyContainsNull() {
        Project project = projectWithSettings("{\"keys\":[null]}");

        assertThatThrownBy(() -> service.findKey(project, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenProjectIsNull() {
        assertThatThrownBy(() -> service.findKey(null, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }

    @Test
    void shouldThrowSigningKeyNotFoundWhenPublicKeyIsBlank() {
        Project project = projectWithSettings("{\"keys\":[{\"keyId\":\"k1\",\"privateKey\":\"a\",\"publicKey\":\" \"}]}");

        assertThatThrownBy(() -> service.findKey(project, "k1"))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }
}

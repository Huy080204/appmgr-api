package com.appmgr.api.service;

import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.dto.project.ProjectKeyDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.dto.project.ProjectSettingsDto;
import com.appmgr.api.model.Project;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@Slf4j
public class ProjectSettingsService {
    @Autowired
    private ObjectMapper objectMapper;

    public ProjectSettingsDto fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readerFor(ProjectSettingsDto.class)
                    .with(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    .readValue(json);
        } catch (JsonProcessingException e) {
            log.error("Cannot parse project settings", e);
            throw new IllegalStateException("Cannot parse project settings", e);
        }
    }

    public ProjectKeyDto findKey(Project project, String keyId) {
        if (project == null) {
            throw signingKeyNotFound();
        }
        ProjectSettingsDto settings;
        try {
            settings = fromJson(project.getSettings());
        } catch (IllegalStateException e) {
            throw signingKeyNotFound();
        }
        if (settings == null || settings.getKeys() == null) {
            throw signingKeyNotFound();
        }
        return settings.getKeys().stream()
                .filter(key -> key != null && Objects.equals(key.getKeyId(), keyId))
                .findFirst()
                .filter(key -> key.getPublicKey() != null && !key.getPublicKey().isBlank())
                .orElseThrow(this::signingKeyNotFound);
    }

    private BadRequestException signingKeyNotFound() {
        return new BadRequestException("Signing key not found", ErrorCode.CHANNEL_ERROR_SIGNING_KEY_NOT_FOUND);
    }
}

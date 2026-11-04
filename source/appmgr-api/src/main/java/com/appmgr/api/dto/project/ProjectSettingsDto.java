package com.appmgr.api.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.Valid;
import java.util.List;

@Getter
@Setter
@Schema
public class ProjectSettingsDto {
    @Valid
    @Schema(name = "keys")
    private List<@Valid ProjectKeyDto> keys;
}

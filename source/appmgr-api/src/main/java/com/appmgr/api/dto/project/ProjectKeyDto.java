package com.appmgr.api.dto.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Schema
public class ProjectKeyDto {
    @NotBlank(message = "keyId cannot be blank")
    @Schema(name = "keyId")
    private String keyId;

    @NotBlank(message = "privateKey cannot be blank")
    @Schema(name = "privateKey", requiredMode = Schema.RequiredMode.REQUIRED)
    private String privateKey;

    @NotBlank(message = "publicKey cannot be blank")
    @Schema(name = "publicKey", requiredMode = Schema.RequiredMode.REQUIRED)
    private String publicKey;
}

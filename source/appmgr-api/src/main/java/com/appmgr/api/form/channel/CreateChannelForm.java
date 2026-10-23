package com.appmgr.api.form.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Schema
public class CreateChannelForm {
    @NotBlank(message = "name cannot be blank")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}

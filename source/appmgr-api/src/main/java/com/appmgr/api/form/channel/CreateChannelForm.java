package com.appmgr.api.form.channel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema
public class CreateChannelForm {
    @NotBlank(message = "name cannot be blank")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}

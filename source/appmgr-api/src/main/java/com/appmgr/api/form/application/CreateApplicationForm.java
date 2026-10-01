package com.appmgr.api.form.application;

import com.appmgr.api.form.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema
public class CreateApplicationForm {
    @NotBlank(message = "name cannot be null")
    @Schema(name = "name", required = true)
    private String name;

    @Schema(name = "description")
    private String description;

    @NotNull(message = "projectId cannot be null")
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @Schema(name = "projectId", required = true)
    private Long projectId;
}

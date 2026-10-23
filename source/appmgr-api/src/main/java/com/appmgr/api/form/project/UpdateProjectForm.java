package com.appmgr.api.form.project;

import com.appmgr.api.form.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class UpdateProjectForm {
    @NotNull(message = "id cannot be null")
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @Schema(name = "id", required = true)
    private Long id;

    @NotBlank(message = "name cannot be null")
    @Schema(name = "name", required = true)
    private String name;

    @NotBlank(message = "description cannot be null")
    @Schema(name = "description", required = true)
    private String description;
}

package com.appmgr.api.form.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema
public class CreateProjectForm {
    @NotBlank(message = "name cannot be null")
    @Schema(name = "name", required = true)
    private String name;

    @NotBlank(message = "description cannot be null")
    @Schema(name = "description", required = true)
    private String description;
}

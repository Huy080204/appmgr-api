package com.appmgr.api.dto.application;

import com.appmgr.api.dto.ABasicAdminDto;
import com.appmgr.api.dto.project.ProjectDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class ApplicationDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;
    @Schema(name = "description")
    private String description;
    @Schema(name = "project")
    private ProjectDto project;
}

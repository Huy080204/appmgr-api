package com.appmgr.api.dto.project;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class ProjectDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;
    @Schema(name = "description")
    private String description;
}

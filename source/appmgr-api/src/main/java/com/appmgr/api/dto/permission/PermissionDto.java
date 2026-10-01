package com.appmgr.api.dto.permission;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class PermissionDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;
    @Schema(name = "action")
    private String action;
    @Schema(name = "showMenu")
    private Boolean showMenu;
    @Schema(name = "description")
    private String description;
    private String pCode;
    private String settings;
    @Schema(name = "nameGroup")
    private String nameGroup;
}

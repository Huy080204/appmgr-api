package com.appmgr.api.dto.group;

import com.appmgr.api.dto.ABasicAdminDto;
import com.appmgr.api.dto.permission.PermissionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema
public class GroupDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;
    @Schema(name = "description")
    private String description;
    @Schema(name = "kind")
    private int kind;
    @Schema(name = "subKind")
    private int subKind;
    @Schema(name = "permissions")
    private List<PermissionDto> permissions;
    @Schema(name = "isSystemRole")
    private Boolean isSystemRole;
}

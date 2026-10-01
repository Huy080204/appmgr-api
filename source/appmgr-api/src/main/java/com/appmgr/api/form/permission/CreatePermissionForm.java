package com.appmgr.api.form.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema
public class CreatePermissionForm {
    @NotBlank(message = "name cant not be null")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @NotBlank(message = "action cant not be null")
    @Schema(name = "action", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;
    @NotNull(message = "showMenu cant not be null")
    @Schema(name = "showMenu", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean showMenu;
    @NotBlank(message = "description cant not be null")
    @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
    @NotBlank(message = "permissionCode cant not be null")
    @Schema(name = "permissionCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String permissionCode;
    @Schema(name = "nameGroup")
    @NotBlank(message = "nameGroup cant not be blank")
    private String nameGroup;
}

package com.appmgr.api.form.permission;

import com.appmgr.api.form.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Data
public class UpdatePermissionForm {
    @NotNull(message = "id cant not be null")
    @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = StringToLongDeserializer.class)
    private Long id;
    @NotEmpty(message = "name cant not be null")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @NotEmpty(message = "action cant not be null")
    @Schema(name = "action", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;
    @NotNull(message = "showMenu cant not be null")
    @Schema(name = "showMenu", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean showMenu;
    @NotEmpty(message = "description cant not be null")
    @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;
    @NotEmpty(message = "permissionCode cant not be null")
    @Schema(name = "permissionCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private String permissionCode;
    @NotEmpty(message = "nameGroup cant not be null")
    @Schema(name = "nameGroup", requiredMode = Schema.RequiredMode.REQUIRED)
    private String nameGroup;
}

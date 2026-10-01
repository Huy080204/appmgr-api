package com.appmgr.api.form.group;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class UpdateGroupForm {
    @NotNull(message = "id cant not be null")
    @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @NotNull(message = "name cant not be null")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(name = "description")
    private String description;
    @NotNull(message = "permissions cant not be null")
    @Schema(name = "permissions", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long[] permissions;
}

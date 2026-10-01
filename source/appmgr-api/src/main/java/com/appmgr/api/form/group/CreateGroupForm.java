package com.appmgr.api.form.group;

import com.appmgr.api.validation.GroupKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class CreateGroupForm {
    @NotEmpty(message = "name cant not be null")
    @Schema(name = "name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotEmpty(message = "description cant not be null")
    @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotNull(message = "permissions cant not be null")
    @Schema(name = "permissions", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long[] permissions;

    @GroupKind
    @Schema(name = "kind", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer kind;
}

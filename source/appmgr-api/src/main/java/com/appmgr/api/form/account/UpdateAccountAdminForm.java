package com.appmgr.api.form.account;

import com.appmgr.api.validation.EmailConstraint;
import com.appmgr.api.validation.PhoneConstraint;
import com.appmgr.api.validation.UsernameConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class UpdateAccountAdminForm {
    @NotNull(message = "id cant not be null")
    @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @UsernameConstraint
    @Schema(name = "username", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(name = "email")
    @EmailConstraint(allowNull = true)
    private String email;

    @PhoneConstraint(allowNull = true)
    @Schema(name = "phone")
    private String phone;

    @Schema(name = "password")
    private String password;

    @NotBlank(message = "fullName cant not be null")
    @Schema(name = "fullName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Schema(name = "avatarPath")
    private String avatarPath;

    @NotNull(message = "groupId cant not be null")
    @Schema(name = "groupId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long groupId;

    @NotNull(message = "status cant not be null")
    @Schema(name = "status", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;
}

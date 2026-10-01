package com.appmgr.api.form.account;

import com.appmgr.api.validation.EmailConstraint;
import com.appmgr.api.validation.PhoneConstraint;
import com.appmgr.api.validation.UsernameConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class CreateAccountAdminForm {
    @UsernameConstraint
    @Schema(name = "username", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @Schema(name = "email")
    @EmailConstraint(allowNull = true)
    private String email;

    @PhoneConstraint(allowNull = true)
    @Schema(name = "phone")
    private String phone;

    @NotEmpty(message = "password cant not be null")
    @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @NotBlank(message = "fullName cant not be null")
    @Schema(name = "fullName", example = "Nguyen Van A", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Schema(name = "avatarPath")
    private String avatarPath;

    @NotNull(message = "status cant not be null")
    @Schema(name = "status", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @NotNull(message = "groupId cant not be null")
    @Schema(name = "groupId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long groupId;
}

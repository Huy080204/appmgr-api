package com.appmgr.api.form.account;

import com.appmgr.api.validation.PhoneConstraint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Schema
public class UpdateProfileAdminForm {
    @Schema(name = "password")
    private String password;

    @NotBlank(message = "oldPassword is required")
    @Schema(name = "oldPassword", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    @NotBlank(message = "fullName is required")
    @Schema(name = "fullName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Schema(name = "avatarPath")
    private String avatarPath;

    @PhoneConstraint(allowNull = true)
    @Schema(name = "phone")
    private String phone;
}

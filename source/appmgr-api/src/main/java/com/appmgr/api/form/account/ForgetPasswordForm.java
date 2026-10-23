package com.appmgr.api.form.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@Schema
public class ForgetPasswordForm {
    @NotEmpty(message = "OPT can not be null.")
    @Schema(name = "otp", requiredMode = Schema.RequiredMode.REQUIRED)
    private String otp;

    @NotEmpty(message = "Email can not be null.")
    @Schema(name = "idHash", requiredMode = Schema.RequiredMode.REQUIRED)
    private String idHash;

    @NotEmpty(message = "newPassword can not be null")
    @Size(min = 6, message = "newPassword minimum 6 character.")
    @Schema(name = "newPassword", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}

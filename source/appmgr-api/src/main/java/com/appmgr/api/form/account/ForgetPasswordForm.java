package com.appmgr.api.form.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

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

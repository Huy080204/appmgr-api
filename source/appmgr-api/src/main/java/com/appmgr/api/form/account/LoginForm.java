package com.appmgr.api.form.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema
public class LoginForm {
    @NotBlank(message = "username cannot be null")
    @Schema(name = "username", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "password cannot be null")
    @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

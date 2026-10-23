package com.appmgr.api.form.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Schema
public class LoginForm {
    @NotBlank(message = "username cannot be null")
    @Schema(name = "username", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "password cannot be null")
    @Schema(name = "password", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}

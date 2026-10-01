package com.appmgr.api.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class ForgetPasswordDto {
    @Schema(name = "idHash")
    private String idHash;
}

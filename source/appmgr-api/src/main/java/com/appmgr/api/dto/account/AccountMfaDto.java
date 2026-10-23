package com.appmgr.api.dto.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class AccountMfaDto {
    @Schema(name = "isMfaEnable")
    private Boolean isMfaEnable;

    @Schema(name = "isMfa")
    private Boolean isMfa;

    @Schema(name = "qrUrl")
    private String qrUrl;
}

package com.appmgr.api.dto.application;

import com.appmgr.api.dto.LongToStringIfWebSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class ApplicationConfigDto {
    @Schema(name = "appId")
    @JsonSerialize(using = LongToStringIfWebSerializer.class)
    private Long appId;
    @Schema(name = "channelId")
    @JsonSerialize(using = LongToStringIfWebSerializer.class)
    private Long channelId;
    @Schema(name = "keyId")
    private String keyId;
    @Schema(name = "publicKey")
    private String publicKey;
}

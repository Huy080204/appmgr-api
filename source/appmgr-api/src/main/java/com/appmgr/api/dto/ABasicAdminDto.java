package com.appmgr.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema
public class ABasicAdminDto {
    @JsonSerialize(using = LongToStringIfWebSerializer.class)
    @Schema(name = "id")
    private Long id;

    @Schema(name = "status")
    private Integer status;

    @Schema(name = "modifiedDate")
    private LocalDateTime modifiedDate;

    @Schema(name = "createdDate")
    private LocalDateTime createdDate;
}

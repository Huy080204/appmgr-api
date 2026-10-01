package com.appmgr.api.dto.channel;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class ChannelDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;
}

package com.appmgr.api.dto.version;

import com.appmgr.api.dto.ABasicAdminDto;
import com.appmgr.api.dto.application.ApplicationDto;
import com.appmgr.api.dto.category.CategoryDto;
import com.appmgr.api.dto.channel.ChannelDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class VersionDto extends ABasicAdminDto {

    @Schema(name = "application")
    private ApplicationDto application;

    @Schema(name = "category")
    private CategoryDto category;

    @Schema(name = "channel")
    private ChannelDto channel;

    @Schema(name = "type")
    private Integer type;

    @Schema(name = "versionCode")
    private Integer versionCode;

    @Schema(name = "versionName")
    private String versionName;

    @Schema(name = "requiredUpdate")
    private Boolean requiredUpdate;

    @Schema(name = "timestamp")
    private Long timestamp;

    @Schema(name = "minVersion")
    private Integer minVersion;

    @Schema(name = "urlBundle")
    private String urlBundle;

    @Schema(name = "runtimeVersion")
    private String runtimeVersion;

    @Schema(name = "manifestId")
    private String manifestId;
}

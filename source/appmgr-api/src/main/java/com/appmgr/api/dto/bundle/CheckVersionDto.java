package com.appmgr.api.dto.bundle;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema
public class CheckVersionDto {

    @Schema(name = "type")
    private Integer type;

    @Schema(name = "versionCode")
    private Integer versionCode;

    @Schema(name = "versionName")
    private String versionName;

    @Schema(name = "minVersion")
    private Integer minVersion;

    @Schema(name = "requiredUpdate")
    private Boolean requiredUpdate;

    @Schema(name = "urlBundle")
    private String urlBundle;
}

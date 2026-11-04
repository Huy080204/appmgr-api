package com.appmgr.api.dto.version;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class VersionDto extends ABasicAdminDto {

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

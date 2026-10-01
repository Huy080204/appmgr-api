package com.appmgr.api.dto.setting;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class SettingDto extends ABasicAdminDto {
    @Schema(name = "groupName")
    private String groupName;
    @Schema(name = "description")
    private String description;
    @Schema(name = "keyName")
    private String keyName;
    @Schema(name = "valueData")
    private String valueData;
    @Schema(name = "dataType")
    private String dataType;
    @Schema(name = "option")
    private String option;
    @Schema(name = "isSystem")
    private Boolean isSystem;
}

package com.appmgr.api.form.setting;

import com.appmgr.api.form.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class UpdateSettingForm {
    @NotNull(message = "id cannot be null")
    @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonDeserialize(using = StringToLongDeserializer.class)
    private Long id;

    @NotBlank(message = "valueData cannot be null")
    @Schema(name = "valueData", requiredMode = Schema.RequiredMode.REQUIRED)
    private String valueData;

    @NotNull(message = "status cannot be null")
    @Schema(name = "status", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @NotBlank(message = "groupName cannot be null")
    @Schema(name = "groupName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String groupName;

    @NotBlank(message = "description cannot be null")
    @Schema(name = "description", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotBlank(message = "keyName cannot be null")
    @Schema(name = "keyName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyName;

    @NotBlank(message = "dataType cannot be null")
    @Schema(name = "dataType", requiredMode = Schema.RequiredMode.REQUIRED)
    private String dataType;

    @Schema(name = "option")
    private String option;
}

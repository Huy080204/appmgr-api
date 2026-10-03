package com.appmgr.api.form.version;

import com.appmgr.api.form.StringToLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema
public class UpdateVersionForm {
    @NotNull(message = "id cannot be null")
    @JsonDeserialize(using = StringToLongDeserializer.class)
    @Schema(name = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @NotNull(message = "versionCode cannot be null")
    @Schema(name = "versionCode", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer versionCode;

    @NotBlank(message = "versionName cannot be null")
    @Schema(name = "versionName", requiredMode = Schema.RequiredMode.REQUIRED)
    private String versionName;

    @NotNull(message = "requiredUpdate cannot be null")
    @Schema(name = "requiredUpdate", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean requiredUpdate;

    @Schema(name = "minVersion")
    private Integer minVersion;

    @Schema(name = "urlBundle")
    private String urlBundle;

    @Schema(name = "runtimeVersion")
    private String runtimeVersion;
}

package com.appmgr.api.form.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@Schema
public class UploadFileForm {
    @NotBlank(message = "type is required")
    @Schema(name = "type", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @NotNull(message = "file is required")
    @Schema(name = "file", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile file;
}

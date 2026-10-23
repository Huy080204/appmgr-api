package com.appmgr.api.form.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema
public class FindByKeyNameForm {
    @NotEmpty(message = "keyNames cannot be empty")
    @Schema(name = "keyNames", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> keyNames;
}

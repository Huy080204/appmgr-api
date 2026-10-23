package com.appmgr.api.form.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema
public class FindByGroupNameForm {
    @NotEmpty(message = "groupNames cannot be empty")
    @Schema(name = "groupNames", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> groupNames;
}

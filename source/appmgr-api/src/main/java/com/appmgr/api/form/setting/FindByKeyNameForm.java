package com.appmgr.api.form.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Schema
public class FindByKeyNameForm {
    @Schema(name = "keyNames")
    private String[] keyNames;

    public List<String> getKeyNames() {
        return Arrays.asList(keyNames);
    }
}

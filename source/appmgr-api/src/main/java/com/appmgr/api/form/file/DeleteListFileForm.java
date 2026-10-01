package com.appmgr.api.form.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@Schema
@AllArgsConstructor
public class DeleteListFileForm {
    @NotNull(message = "files cannot be null!")
    private List<String> files;
}

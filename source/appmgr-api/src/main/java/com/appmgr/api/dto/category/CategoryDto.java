package com.appmgr.api.dto.category;

import com.appmgr.api.dto.ABasicAdminDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class CategoryDto extends ABasicAdminDto {
    @Schema(name = "name")
    private String name;

    @Schema(name = "description")
    private String description;

    @Schema(name = "avatar")
    private String avatar;
}

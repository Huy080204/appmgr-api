package com.appmgr.api.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.Resource;

@Getter
@Setter
@AllArgsConstructor
public class OtaAsset {
    private Resource resource;
    private String mediaType;
}

package com.appmgr.api.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;

@Data
@AllArgsConstructor
public class OtaAsset {
    private Resource resource;
    private String mediaType;
}

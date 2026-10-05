package com.appmgr.api.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OtaPart {
    private String name;
    private String json;
    private String signatureHeader;
}

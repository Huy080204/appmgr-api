package com.appmgr.api.dto.ota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OtaPart {
    private String name;
    private String json;
    private String signatureHeader;
}

package com.appmgr.api.dto.bundle;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;

@Data
@AllArgsConstructor
public class BundleFile {
    private Path path;
    private String fileName;
    private String mediaType;
}

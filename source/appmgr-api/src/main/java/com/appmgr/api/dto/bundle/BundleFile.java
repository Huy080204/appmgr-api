package com.appmgr.api.dto.bundle;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;

@Getter
@Setter
@AllArgsConstructor
public class BundleFile {
    private Path path;
    private String fileName;
    private String mediaType;
}

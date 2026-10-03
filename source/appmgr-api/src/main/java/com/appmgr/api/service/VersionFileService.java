package com.appmgr.api.service;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.model.Application;
import com.appmgr.api.model.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@Slf4j
public class VersionFileService {

    private static final String EXTENSION_APK = "apk";
    private static final String EXTENSION_TAR_GZ = "tar.gz";

    @Value("${file.upload-dir}")
    private String rootDirectory;

    public Path resolveVersionDir(Long appId, String channelName, Long versionId) {
        Path appDir = Paths.get(rootDirectory).toAbsolutePath().normalize()
                .resolve(BaseConstant.VERSION_FOLDER)
                .resolve(String.valueOf(appId));
        Path channelDir;
        try {
            channelDir = appDir.resolve(channelName).normalize();
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid channel name", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        }
        if (!appDir.equals(channelDir.getParent())) {
            throw new BadRequestException("Invalid channel name", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        }
        return channelDir.resolve(String.valueOf(versionId));
    }

    public String resolveBundleExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith("." + EXTENSION_TAR_GZ)) {
            return EXTENSION_TAR_GZ;
        }
        if (lowerName.endsWith("." + EXTENSION_APK)) {
            return EXTENSION_APK;
        }
        return null;
    }

    public void storeVersionBundle(Version version, MultipartFile file) {
        Application application = version.getApplication();
        Path versionDir = resolveVersionDir(application.getId(), version.getChannel().getName(), version.getId());
        String fileName = application.getName() + "_" + version.getVersionName() + "_"
                + version.getVersionCode() + "." + resolveBundleExtension(file.getOriginalFilename());
        try {
            storeBundle(file, versionDir, fileName);
        } catch (BadRequestException e) {
            deleteVersionDir(versionDir);
            throw e;
        }
    }

    public void storeBundle(MultipartFile file, Path versionDir, String fileName) {
        Path target = resolveBundleTarget(versionDir, fileName);
        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(versionDir);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Store bundle failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
    }

    private Path resolveBundleTarget(Path versionDir, String fileName) {
        Path target;
        try {
            target = versionDir.resolve(fileName).normalize();
        } catch (InvalidPathException e) {
            throw new BadRequestException("Invalid bundle file name");
        }
        if (!versionDir.normalize().equals(target.getParent())) {
            throw new BadRequestException("Invalid bundle file name");
        }
        return target;
    }

    public void deleteVersionDir(Path versionDir) {
        try {
            FileSystemUtils.deleteRecursively(versionDir);
        } catch (IOException e) {
            log.warn("Delete version dir {} failed: {}", versionDir, e.getMessage());
        }
    }
}

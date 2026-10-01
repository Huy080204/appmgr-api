package com.appmgr.api.service;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.dto.file.UploadFileDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.form.file.UploadFileForm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class FileService {
    static final String[] UPLOAD_TYPES = new String[]{"AVATAR", "LOGO", "SETTING"};
    static final String[] AVATAR_EXTENSION = new String[]{"jpeg", "jpg", "gif", "bmp", "png"};

    @Value("${file.upload-dir}")
    private String ROOT_DIRECTORY;

    public ApiMessageDto<UploadFileDto> storeFile(UploadFileForm uploadFileForm) {
        ApiMessageDto<UploadFileDto> apiMessageDto = new ApiMessageDto<>();
        try {
            String type = findUploadType(uploadFileForm.getType());
            if (type == null) {
                throw new BadRequestException("ERROR-UPLOAD-TYPE-INVALID", "Type is required in AVATAR, LOGO, SETTING");
            }
            String fileName = StringUtils.cleanPath(uploadFileForm.getFile().getOriginalFilename());
            String extension = FilenameUtils.getExtension(fileName);
            if ((type.equals("AVATAR") || type.equals("LOGO"))
                    && !Arrays.stream(AVATAR_EXTENSION).anyMatch(extension::equalsIgnoreCase)) {
                throw new BadRequestException("ERROR-FILE-FORMAT-INVALID", "File format is invalid");
            }
            //upload to uploadFolder/TYPE/id
            String finalFile = type + "_" + RandomStringUtils.randomAlphanumeric(10) + "." + extension;
            String typeFolder = File.separator + type;

            Path fileStorageLocation = Paths.get(ROOT_DIRECTORY + typeFolder).toAbsolutePath().normalize();
            Files.createDirectories(fileStorageLocation);
            Path targetLocation = fileStorageLocation.resolve(finalFile);
            Files.copy(uploadFileForm.getFile().getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            UploadFileDto uploadFileDto = new UploadFileDto();
            uploadFileDto.setFilePath(typeFolder + File.separator + finalFile);
            apiMessageDto.setData(uploadFileDto);
            apiMessageDto.setMessage("Upload file success");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            apiMessageDto.setResult(false);
            apiMessageDto.setMessage("" + e.getMessage());
        }
        return apiMessageDto;
    }

    /** Only a regular file inside {ROOT}/{AVATAR|LOGO|SETTING} is returned, otherwise null. */
    public Resource loadFileAsResource(String folder, String fileName) {
        if (findUploadType(folder) == null) {
            log.warn("[File] folder is not an upload type: {}", folder);
            return null;
        }
        Path folderDir = resolveInside(getRoot(), folder);
        Path fP = folderDir == null ? null : resolveInside(folderDir, fileName);
        if (fP == null || fP.equals(folderDir) || !isRealPathInside(folderDir, fP)
                || !Files.isRegularFile(fP, LinkOption.NOFOLLOW_LINKS)) {
            log.warn("[File] invalid path: {}/{}", folder, fileName);
            return null;
        }
        try {
            Resource resource = new UrlResource(fP.toUri());
            if (resource.exists()) {
                return resource;
            }
        } catch (MalformedURLException ex) {
            log.error(ex.getMessage(), ex);
        }
        return null;
    }

    public void deleteFile(String filePath) {
        Path target = resolveDeletable(filePath);
        if (target == null) {
            return;
        }
        try {
            Files.delete(target);
        } catch (IOException e) {
            log.error("Error deleting file: {}", target, e);
        }
    }

    public void deleteFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            deleteFile(filePath);
        }
    }

    /** filePath (as returned by upload) = /{TYPE}/{fileName}; must be a regular file inside the TYPE folder. */
    private Path resolveDeletable(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        String[] parts = filePath.replace('\\', '/').replaceFirst("^/+", "").split("/", 2);
        if (parts.length != 2 || findUploadType(parts[0]) == null) {
            log.warn("Skip delete file, path is outside upload folders: {}", filePath);
            return null;
        }
        Path folderDir = resolveInside(getRoot(), parts[0]);
        Path target = folderDir == null ? null : resolveInside(folderDir, parts[1]);
        if (target == null || target.equals(folderDir) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || !isRealPathInside(folderDir, target)) {
            log.warn("Skip delete file, invalid or not a regular file: {}", filePath);
            return null;
        }
        return target;
    }

    private static String findUploadType(String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(UPLOAD_TYPES).filter(value::equalsIgnoreCase).findFirst().orElse(null);
    }

    private Path getRoot() {
        return Paths.get(ROOT_DIRECTORY).toAbsolutePath().normalize();
    }

    private Path resolveInside(Path base, String relative) {
        String cleaned = relative;
        while (cleaned.startsWith("/") || cleaned.startsWith("\\")) {
            cleaned = cleaned.substring(1);
        }
        Path resolved;
        try {
            resolved = base.resolve(cleaned).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
        return resolved.startsWith(base) ? resolved : null;
    }

    /** Guards against a symlinked directory inside base pointing outside it. */
    private boolean isRealPathInside(Path base, Path target) {
        try {
            return target.toRealPath().startsWith(base.toRealPath());
        } catch (IOException e) {
            return false;
        }
    }
}

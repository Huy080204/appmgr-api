package com.appmgr.api.service;

import com.appmgr.api.dto.ErrorCode;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.model.Version;
import com.appmgr.api.repository.VersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
@Slf4j
public class OtaService {

    private static final String METADATA_FILE = "metadata.json";
    private static final String EXTENSION_ZIP = ".zip";
    private static final String HASH_ALGORITHM = "SHA-256";

    @Autowired
    private VersionRepository versionRepository;

    @Autowired
    private VersionFileService versionFileService;

    @Transactional
    public Version publish(Version version, MultipartFile file) {
        validateOtaZip(file);
        versionRepository.save(version);
        Path versionDir = versionFileService.resolveVersionDir(
                version.getApplication().getId(), version.getChannel().getName(), version.getId());
        try {
            extractOtaZip(file, versionDir);
            version.setManifestId(computeManifestId(versionDir.resolve(METADATA_FILE)));
        } catch (BadRequestException e) {
            versionFileService.deleteVersionDir(versionDir);
            throw e;
        }
        versionRepository.save(version);
        return version;
    }

    private void validateOtaZip(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(EXTENSION_ZIP)) {
            throw new BadRequestException("OTA file must be a zip");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("OTA file is required");
        }
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && METADATA_FILE.equals(entry.getName())) {
                    return;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        } catch (IOException e) {
            throw new BadRequestException("Read OTA zip failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
        throw new BadRequestException("metadata.json not found at zip root", ErrorCode.VERSION_ERROR_METADATA_NOT_FOUND);
    }

    private void extractOtaZip(MultipartFile file, Path versionDir) {
        Path targetDir = versionDir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir) || target.equals(targetDir)) {
                    throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (ZipException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid zip entry", ErrorCode.VERSION_ERROR_ZIP_ENTRY_INVALID);
        } catch (IOException e) {
            throw new BadRequestException("Extract OTA zip failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
    }

    private String computeManifestId(Path metadataFile) {
        try {
            byte[] digest = MessageDigest.getInstance(HASH_ALGORITHM).digest(Files.readAllBytes(metadataFile));
            String hex = toHex(digest).substring(0, 32);
            return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                    + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BadRequestException("Compute manifest id failed", ErrorCode.VERSION_ERROR_STORE_FILE_FAILED);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

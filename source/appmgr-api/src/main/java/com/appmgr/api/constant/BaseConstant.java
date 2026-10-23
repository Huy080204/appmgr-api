package com.appmgr.api.constant;

import java.util.List;

public class BaseConstant {
    public static final String DEFAULT_TIMEZONE = "UTC";

    public static final String DATE_FORMAT = "dd/MM/yyyy";
    public static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm:ss";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";
    public static final String HEADER_CLIENT_TYPE_WEB = "WEB";

    public static final String PHONE_PATTERN = "^0[35789][0-9]{8}$";
    public static final String EMAIL_PATTERN = "^(?!.*[.]{2,})[a-zA-Z0-9.%]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final String USERNAME_PATTERN = "^(?=.{3,20}$)(?!.*[_.]{2})[a-zA-Z][a-zA-Z0-9_]*[a-zA-Z0-9]$";
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";

    public static final Integer USER_KIND_ADMIN = 1;

    public static final Integer STATUS_ACTIVE = 1;
    public static final Integer STATUS_PENDING = 0;
    public static final Integer STATUS_LOCK = -1;
    public static final Integer STATUS_DELETE = -2;

    public static final Integer GROUP_KIND_ADMIN = 1;

    // Version type: 1 = bundle, 2 = store, 3 = OTA
    public static final Integer VERSION_TYPE_BUNDLE = 1;
    public static final Integer VERSION_TYPE_STORE = 2;
    public static final Integer VERSION_TYPE_OTA = 3;

    // Bundle: types for check-version
    public static final List<Integer> BUNDLE_VERSION_TYPES = List.of(VERSION_TYPE_BUNDLE, VERSION_TYPE_STORE);

    // Bundle: extensions and media types
    public static final String BUNDLE_EXTENSION_APK = "apk";
    public static final String BUNDLE_EXTENSION_TAR_GZ = "tar.gz";
    public static final String BUNDLE_MEDIA_TYPE_APK = "application/vnd.android.package-archive";
    public static final String BUNDLE_MEDIA_TYPE_TAR_GZ = "application/gzip";

    // Version files: folder and download path
    public static final String VERSION_FOLDER = "APP_VERSION";
    public static final String VERSION_DOWNLOAD_PATH = "/v1/bundle/download-version";

    // OTA: manifest
    public static final String OTA_PLATFORM_IOS = "ios";
    public static final String OTA_PLATFORM_ANDROID = "android";
    public static final String OTA_ASSETS_PATH = "/v1/ota/assets";
    public static final String OTA_METADATA_FILE = "metadata.json";
    public static final String OTA_PART_MANIFEST = "manifest";
    public static final String OTA_PART_DIRECTIVE = "directive";
    public static final String OTA_DIRECTIVE_NO_UPDATE = "noUpdateAvailable";
    public static final String OTA_SIGNATURE_ALGORITHM = "rsa-v1_5-sha256";
    public static final String OTA_MEDIA_TYPE_JAVASCRIPT = "application/javascript";
    public static final String OTA_MEDIA_TYPE_DEFAULT = "application/octet-stream";
    public static final String OTA_BUNDLE_EXTENSION = ".bundle";
    public static final String OTA_PROTOCOL_VERSION = "1";
    public static final String OTA_SFV_VERSION = "0";
    public static final String OTA_CACHE_CONTROL = "private, max-age=0";

    // OTA: request
    public static final String OTA_HEADER_APP_ID = "x-app-id";
    public static final String OTA_HEADER_CATEGORY_ID = "x-category-id";
    public static final String OTA_HEADER_CHANNEL_NAME = "expo-channel-name";
    public static final String OTA_HEADER_PLATFORM = "expo-platform";
    public static final String OTA_HEADER_RUNTIME_VERSION = "expo-runtime-version";
    public static final String OTA_HEADER_CURRENT_UPDATE_ID = "expo-current-update-id";

    // OTA: response headers
    public static final String OTA_HEADER_PROTOCOL_VERSION = "expo-protocol-version";
    public static final String OTA_HEADER_SFV_VERSION = "expo-sfv-version";
    public static final String OTA_HEADER_SIGNATURE = "expo-signature";

    // OTA: response body
    public static final String OTA_CRLF = "\r\n";
    public static final String OTA_BOUNDARY_PREFIX = "--";
    public static final String OTA_PART_DISPOSITION_PREFIX = "Content-Disposition: form-data; name=\"";
    public static final String OTA_PART_CONTENT_TYPE = "Content-Type: application/json; charset=utf-8";
    public static final String OTA_MEDIA_TYPE_MULTIPART = "multipart/mixed; boundary=";

    public static final String OTA_EXTENSION_ZIP = ".zip";
    public static final String OTA_HASH_SHA256 = "SHA-256";
    public static final String OTA_HASH_MD5 = "MD5";
    public static final String OTA_SIGNATURE_JCA_ALGORITHM = "SHA256withRSA";
    public static final String OTA_METADATA_REPUBLISHED_FROM = "_republishedFrom";
    public static final String OTA_METADATA_REPUBLISHED_AT = "_republishedAt";

    private BaseConstant() {
        throw new IllegalStateException("Utility class");
    }
}

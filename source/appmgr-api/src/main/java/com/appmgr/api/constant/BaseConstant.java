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

    public static final Integer VERSION_TYPE_BUNDLE = 1;
    public static final Integer VERSION_TYPE_STORE = 2;
    public static final Integer VERSION_TYPE_OTA = 3;

    public static final List<Integer> BUNDLE_VERSION_TYPES = List.of(VERSION_TYPE_BUNDLE, VERSION_TYPE_STORE);

    public static final String BUNDLE_EXTENSION_APK = "apk";
    public static final String BUNDLE_EXTENSION_TAR_GZ = "tar.gz";
    public static final String BUNDLE_MEDIA_TYPE_APK = "application/vnd.android.package-archive";
    public static final String BUNDLE_MEDIA_TYPE_TAR_GZ = "application/gzip";

    public static final String VERSION_FOLDER = "APP_VERSION";
    public static final String VERSION_DOWNLOAD_PATH = "/v1/bundle/download-version";

    private BaseConstant() {
        throw new IllegalStateException("Utility class");
    }
}

package com.appmgr.api.dto;

public class ErrorCode {
    /**
     * Authentication error code
     */
    public static final String AUTH_GRANT_TYPE_PASSWORD_MFA_ERROR_OTP_BLANK = "ERROR-AUTH-0000";

    /**
     * Group error code
     */
    public static final String GROUP_ERROR_NAME_EXIST = "ERROR-GROUP-000";
    public static final String GROUP_ERROR_NOT_FOUND = "ERROR-GROUP-001";

    /**
     * Permission error code
     */
    public static final String PERMISSION_ERROR_NAME_EXIST = "ERROR-PERMISSION-000";
    public static final String PERMISSION_ERROR_CODE_EXIST = "ERROR-PERMISSION-001";
    public static final String PERMISSION_ERROR_NOT_FOUND = "ERROR-PERMISSION-002";

    /**
     * Project error code
     */
    public static final String PROJECT_ERROR_NAME_EXIST = "ERROR-PROJECT-000";
    public static final String PROJECT_ERROR_NOT_FOUND = "ERROR-PROJECT-001";

    /**
     * Application error code
     */
    public static final String APPLICATION_ERROR_NAME_EXIST = "ERROR-APPLICATION-000";
    public static final String APPLICATION_ERROR_NOT_FOUND = "ERROR-APPLICATION-001";

    /**
     * Starting error code Account
     */
    public static final String ACCOUNT_ERROR_UNKNOWN = "ERROR-ACCOUNT-0000";
    public static final String ACCOUNT_ERROR_USERNAME_EXIST = "ERROR-ACCOUNT-0001";
    public static final String ACCOUNT_ERROR_NOT_FOUND = "ERROR-ACCOUNT-0002";
    public static final String ACCOUNT_ERROR_WRONG_PASSWORD = "ERROR-ACCOUNT-0003";
    public static final String ACCOUNT_ERROR_WRONG_HASH_RESET_PASS = "ERROR-ACCOUNT-0004";
    public static final String ACCOUNT_ERROR_LOCKED = "ERROR-ACCOUNT-0005";
    public static final String ACCOUNT_ERROR_OTP_INVALID = "ERROR-ACCOUNT-0006";
    public static final String ACCOUNT_ERROR_LOGIN = "ERROR-ACCOUNT-0007";
    public static final String ACCOUNT_ERROR_SOCIAL_LOGIN_FAIL = "ERROR-ACCOUNT-ERROR-0008";
    public static final String ACCOUNT_ERROR_NOT_DELETE_SUPPER_ADMIN = "ERROR-ACCOUNT-00014";
    public static final String ACCOUNT_ERROR_EMAIL_EXISTED = "ERROR-ACCOUNT-00015";
    public static final String ACCOUNT_ERROR_PHONE_EXISTED = "ERROR-ACCOUNT-00016";
    public static final String ACCOUNT_ERROR_NEW_PASSWORD_SAME_OLD_PASSWORD = "ERROR-ACCOUNT-00017";

    /**
     * GroupPermission error code
     */
    public static final String GROUP_PERMISSION_ERROR_NOT_FOUND = "ERROR-GROUP-PERMISSION-000";
    public static final String GROUP_PERMISSION_ERROR_NAME_EXIST = "ERROR-GROUP-PERMISSION-001";

    /**
     * Category error code
     */
    public static final String CATEGORY_ERROR_NAME_EXIST = "ERROR-CATEGORY-000";
    public static final String CATEGORY_ERROR_NOT_FOUND = "ERROR-CATEGORY-001";

    /**
     * Setting error code
     */
    public static final String SETTING_ERROR_NOT_FOUND = "ERROR-SETTING-000";
    public static final String SETTING_ERROR_EXISTED_GROUP_NAME_AND_KEY_NAME = "ERROR-SETTING-001";

    /**
     * Channel error code
     */
    public static final String CHANNEL_ERROR_NOT_FOUND = "ERROR-CHANNEL-000";
    public static final String CHANNEL_ERROR_NAME_EXISTED = "ERROR-CHANNEL-001";

    /**
     * Version error code
     */
    public static final String VERSION_ERROR_NOT_FOUND = "ERROR-VERSION-000";
    public static final String VERSION_ERROR_METADATA_NOT_FOUND = "ERROR-VERSION-001";
    public static final String VERSION_ERROR_ZIP_ENTRY_INVALID = "ERROR-VERSION-002";
    public static final String VERSION_ERROR_STORE_FILE_FAILED = "ERROR-VERSION-003";
}

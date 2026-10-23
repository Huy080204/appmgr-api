package com.appmgr.api.config;

public class SecurityConstant {
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String GRANT_TYPE_PASSWORD_MFA = "password_mfa";
    public static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    public static final String TOKEN_ENDPOINT = "/api/token";

    public static final String PARAMETER_USERNAME = "username";
    public static final String PARAMETER_PASSWORD = "password";
    public static final String PARAMETER_OTP = "otp";

    public static final String CLAIM_VERSION = "version";
    public static final String CLAIM_CLIENT_ID = "client_id";
    public static final String CLAIM_USER_NAME = "user_name";
    public static final String CLAIM_AUTHORITIES = "authorities";
    public static final String CLAIM_GRANT_TYPE = "grant_type";
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_ADDITIONAL_INFO = "additional_info";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    public static final String JWT_HEADER_TYPE = "JWT";
    public static final int MIN_SIGNING_KEY_BYTES = 32;
}

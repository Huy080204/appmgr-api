package com.appmgr.api.exception;

import org.springframework.security.oauth2.common.exceptions.ClientAuthenticationException;

public class ForceTokenExpired extends ClientAuthenticationException {
    public ForceTokenExpired(String msg) {
        super(msg);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "invalid_token";
    }

    @Override
    public int getHttpErrorCode() {
        return 401;
    }
}

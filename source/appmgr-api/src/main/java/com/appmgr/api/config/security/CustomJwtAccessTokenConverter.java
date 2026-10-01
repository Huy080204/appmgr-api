package com.appmgr.api.config.security;

import com.appmgr.api.config.SecurityConstant;
import com.appmgr.api.exception.ForceTokenExpired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import java.util.Map;
import java.util.Objects;

@Slf4j
public class CustomJwtAccessTokenConverter extends JwtAccessTokenConverter {
    private int currentVersion;

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    @Override
    public OAuth2Authentication extractAuthentication(Map<String, ?> claims) {
        String grantType = (String) claims.get("grant_type");

        if (Objects.equals(grantType, SecurityConstant.GRANT_TYPE_PASSWORD)) {
            Integer tokenVersion = (Integer) claims.get("version");
            if (tokenVersion == null || tokenVersion < currentVersion) {
                throw new ForceTokenExpired("Token has been force expired (version mismatch)");
            }
        }
        // Nếu hợp lệ thì gọi logic gốc
        return super.extractAuthentication(claims);
    }
}

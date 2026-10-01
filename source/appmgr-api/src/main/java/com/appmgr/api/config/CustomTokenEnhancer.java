package com.appmgr.api.config;

import com.appmgr.api.constant.DatabaseConstant;
import com.appmgr.api.dto.AccountForTokenDto;
import com.appmgr.api.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CustomTokenEnhancer implements TokenEnhancer {
    private JdbcTemplate jdbcTemplate;
    private Integer currentVersion;

    String prefix = DatabaseConstant.PREFIX_TABLE;

    public CustomTokenEnhancer(JdbcTemplate jdbcTemplate, Integer currentVersion) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentVersion = currentVersion;
    }

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        Map<String, Object> additionalInfo;
        String username = authentication.getName();

        String grantType = authentication.getOAuth2Request().getRequestParameters().get("grant_type");
        if (SecurityConstant.GRANT_TYPE_PASSWORD_MFA.equals(grantType)) {
            additionalInfo = getAdditionalInfoForPasswordMfa(username);
        } else {
            additionalInfo = getAdditionalInfo(username, authentication.getOAuth2Request().getGrantType());
        }
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInfo);
        return accessToken;
    }

    private Map<String, Object> getAdditionalInfo(String username, String grantType) {
        Map<String, Object> additionalInfo = new HashMap<>();
        AccountForTokenDto a = getAccountByUsername(username);

        if (a != null) {
            Long accountId = a.getId();
            Long restaurantId = -1L;
            String kind = a.getKind() + "";//token kind
            Long deviceId = -1L;// id cua thiet bi, lưu ở table device để get firebase url..
            String pemission = "<>";//empty string
            Integer userKind = a.getKind(); //loại user là admin hay là gì
            Integer tabletKind = -1;
            Long orderId = -1L;
            Boolean isSuperAdmin = a.getIsSuperAdmin();
            String tenantId = "<>";
            additionalInfo.put("user_id", a.getId());
            additionalInfo.put("user_kind", a.getKind());
            additionalInfo.put("grant_type", SecurityConstant.GRANT_TYPE_PASSWORD);
            additionalInfo.put("version", currentVersion);
            String DELIM = "|";
            String additionalInfoStr = ZipUtils.zipString(accountId + DELIM
                    + restaurantId + DELIM
                    + kind + DELIM
                    + pemission + DELIM
                    + deviceId + DELIM
                    + userKind + DELIM
                    + username + DELIM
                    + tabletKind + DELIM
                    + orderId + DELIM
                    + isSuperAdmin + DELIM
                    + tenantId);
            additionalInfo.put("additional_info", additionalInfoStr);
        }
        return additionalInfo;
    }

    private Map<String, Object> getAdditionalInfoForPasswordMfa(String username) {
        Map<String, Object> additionalInfo = new HashMap<>();
        AccountForTokenDto a = getAccountByUsername(username);

        if (a != null) {
            Long accountId = a.getId();
            Long restaurantId = -1L;
            String kind = a.getKind() + "";//token kind
            Long deviceId = -1L;// id cua thiet bi, lưu ở table device để get firebase url..
            String pemission = "<>";//empty string
            Integer userKind = a.getKind(); //loại user là admin hay là gì
            Integer tabletKind = -1;
            Long orderId = -1L;
            Boolean isSuperAdmin = a.getIsSuperAdmin();
            String tenantId = "<>";
            additionalInfo.put("user_id", a.getId());
            additionalInfo.put("user_kind", a.getKind());
            additionalInfo.put("grant_type", SecurityConstant.GRANT_TYPE_PASSWORD_MFA);
            additionalInfo.put("version", currentVersion);
            String DELIM = "|";
            String additionalInfoStr = ZipUtils.zipString(accountId + DELIM
                    + restaurantId + DELIM
                    + kind + DELIM
                    + pemission + DELIM
                    + deviceId + DELIM
                    + userKind + DELIM
                    + username + DELIM
                    + tabletKind + DELIM
                    + orderId + DELIM
                    + isSuperAdmin + DELIM
                    + tenantId);
            additionalInfo.put("additional_info", additionalInfoStr);
        }
        return additionalInfo;
    }

    public AccountForTokenDto getAccountByUsername(String username) {
        try {
            String query = "SELECT id, kind, username, email, full_name, is_super_admin " +
                    "FROM " + prefix + "account " +
                    "WHERE username = ? AND status = 1 LIMIT 1";
            List<AccountForTokenDto> dto = jdbcTemplate.query(query, new Object[]{username}, new BeanPropertyRowMapper<>(AccountForTokenDto.class));
            if (!dto.isEmpty()) return dto.get(0);
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

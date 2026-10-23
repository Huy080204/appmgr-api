package com.appmgr.api.config;

import com.appmgr.api.constant.DatabaseConstant;
import com.appmgr.api.dto.AccountForTokenDto;
import com.appmgr.api.utils.ZipUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.appmgr.api.config.SecurityConstant.CLAIM_ADDITIONAL_INFO;
import static com.appmgr.api.config.SecurityConstant.CLAIM_AUTHORITIES;
import static com.appmgr.api.config.SecurityConstant.CLAIM_CLIENT_ID;
import static com.appmgr.api.config.SecurityConstant.CLAIM_GRANT_TYPE;
import static com.appmgr.api.config.SecurityConstant.CLAIM_USER_NAME;
import static com.appmgr.api.config.SecurityConstant.CLAIM_VERSION;
import static com.appmgr.api.config.SecurityConstant.GRANT_TYPE_PASSWORD;
import static com.appmgr.api.config.SecurityConstant.GRANT_TYPE_PASSWORD_MFA;
import static com.appmgr.api.config.SecurityConstant.JWT_HEADER_TYPE;

@Slf4j
@Component
public class CustomTokenEnhancer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${jwt.version}")
    private Integer currentVersion;

    String prefix = DatabaseConstant.PREFIX_TABLE;

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        context.getJwsHeader().algorithm(MacAlgorithm.HS256).type(JWT_HEADER_TYPE);
        context.getClaims().claim(CLAIM_VERSION, currentVersion)
                .claim(CLAIM_CLIENT_ID, context.getRegisteredClient().getClientId());

        String grantType = context.getAuthorizationGrantType().getValue();
        if (!CustomTokenGranter.GRANT_TYPES.contains(grantType)) {
            return;
        }
        String username = context.getPrincipal().getName();
        AccountForTokenDto account = getAccountByUsername(username);
        if (account == null) {
            return;
        }
        context.getClaims()
                .claim(CLAIM_USER_NAME, username)
                .claim(CLAIM_AUTHORITIES, authorityNames(context.getPrincipal().getAuthorities()));
        getAdditionalInfo(grantType, account, username).forEach(context.getClaims()::claim);
    }

    private Map<String, Object> getAdditionalInfo(String grantType, AccountForTokenDto account, String username) {
        switch (grantType) {
            case GRANT_TYPE_PASSWORD:
                return getAdditionalInfoForAccount(account, username, GRANT_TYPE_PASSWORD);
            case GRANT_TYPE_PASSWORD_MFA:
                return getAdditionalInfoForAccount(account, username, GRANT_TYPE_PASSWORD_MFA);
            default:
                return Map.of();
        }
    }

    private Map<String, Object> getAdditionalInfoForAccount(AccountForTokenDto a, String username, String grantType) {
        Map<String, Object> additionalInfo = new HashMap<>();
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
        additionalInfo.put(CLAIM_GRANT_TYPE, grantType);
        additionalInfo.put(CLAIM_VERSION, currentVersion);
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
        additionalInfo.put(CLAIM_ADDITIONAL_INFO, additionalInfoStr);
        return additionalInfo;
    }

    private static List<String> authorityNames(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
    }

    public AccountForTokenDto getAccountByUsername(String username) {
        try {
            String query = "SELECT id, kind, username, email, full_name, is_super_admin " +
                    "FROM " + prefix + "account " +
                    "WHERE username = ? AND status = 1 LIMIT 1";
            List<AccountForTokenDto> dto = jdbcTemplate.query(query, new BeanPropertyRowMapper<>(AccountForTokenDto.class), username);
            if (!dto.isEmpty()) return dto.get(0);
            return null;
        } catch (DataAccessException e) {
            log.error("Cannot load account {} for token", username, e);
            return null;
        }
    }
}

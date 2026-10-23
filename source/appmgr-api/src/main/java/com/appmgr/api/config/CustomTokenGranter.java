package com.appmgr.api.config;

import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.appmgr.api.config.SecurityConstant.CLAIM_CLIENT_ID;
import static com.appmgr.api.config.SecurityConstant.CLAIM_GRANT_TYPE;
import static com.appmgr.api.config.SecurityConstant.CLAIM_USER_NAME;
import static com.appmgr.api.config.SecurityConstant.GRANT_TYPE_PASSWORD;
import static com.appmgr.api.config.SecurityConstant.GRANT_TYPE_PASSWORD_MFA;
import static com.appmgr.api.config.SecurityConstant.GRANT_TYPE_REFRESH_TOKEN;
import static com.appmgr.api.config.SecurityConstant.PARAMETER_OTP;
import static com.appmgr.api.config.SecurityConstant.PARAMETER_PASSWORD;
import static com.appmgr.api.config.SecurityConstant.PARAMETER_USERNAME;

public class CustomTokenGranter implements AuthenticationProvider {
    public static final Set<String> GRANT_TYPES = Set.of(GRANT_TYPE_PASSWORD, GRANT_TYPE_PASSWORD_MFA);

    private final AuthenticationManager authenticationManager;
    private final UserServiceImpl userService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final JwtDecoder refreshTokenDecoder;

    public CustomTokenGranter(AuthenticationManager authenticationManager, UserServiceImpl userService,
                              OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator, JwtDecoder refreshTokenDecoder) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenDecoder = refreshTokenDecoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        GrantToken grant = (GrantToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = authenticatedClient(grant);
        RegisteredClient client = clientPrincipal.getRegisteredClient();
        if (!client.getAuthorizationGrantTypes().contains(grant.getGrantType())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        UserLogin login = authenticateUserOrInvalidGrant(grant, client);
        DefaultOAuth2TokenContext.Builder tokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(client).principal(login.user())
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(login.scopes())
                .authorizationGrantType(login.grantType())
                .authorizationGrant(grant);

        OAuth2Token token = tokenGenerator.generate(tokenContext.tokenType(OAuth2TokenType.ACCESS_TOKEN).build());
        if (token == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "The token generator failed to generate the access token.", null));
        }
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt(), login.scopes());

        OAuth2RefreshToken refreshToken = null;
        if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            OAuth2Token generated = tokenGenerator.generate(tokenContext.tokenType(OAuth2TokenType.REFRESH_TOKEN).build());
            if (!(generated instanceof OAuth2RefreshToken)) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                        "The token generator failed to generate a valid refresh token.", null));
            }
            refreshToken = (OAuth2RefreshToken) generated;
        }
        return new OAuth2AccessTokenAuthenticationToken(client, clientPrincipal, accessToken, refreshToken);
    }

    private static OAuth2ClientAuthenticationToken authenticatedClient(GrantToken grant) {
        Object principal = grant.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken && ((OAuth2ClientAuthenticationToken) principal).isAuthenticated()) {
            return (OAuth2ClientAuthenticationToken) principal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    private UserLogin authenticateUser(GrantToken grant, RegisteredClient client) {
        String username = grant.parameter(PARAMETER_USERNAME);
        String password = grant.parameter(PARAMETER_PASSWORD);
        switch (grant.getGrantType().getValue()) {
            case GRANT_TYPE_PASSWORD:
                Set<String> passwordScopes = resolveScopes(grant, client.getScopes());
                return new UserLogin(authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(username, password)), grant.getGrantType(), passwordScopes);
            case GRANT_TYPE_PASSWORD_MFA:
                Set<String> mfaScopes = resolveScopes(grant, client.getScopes());
                UserDetails user = userService.loadUserForPasswordOtp(username, password, grant.parameter(PARAMETER_OTP));
                return new UserLogin(authenticated(user), grant.getGrantType(), mfaScopes);
            case GRANT_TYPE_REFRESH_TOKEN:
                return refresh(grant.parameter(OAuth2ParameterNames.REFRESH_TOKEN), grant, client);
            default:
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE);
        }
    }

    private static Set<String> resolveScopes(GrantToken grant, Set<String> allowed) {
        String requested = grant.parameter(OAuth2ParameterNames.SCOPE);
        if (!StringUtils.hasText(requested)) {
            return allowed;
        }
        Set<String> scopes = new LinkedHashSet<>(Arrays.asList(requested.trim().split("\\s+")));
        if (!allowed.containsAll(scopes)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE, "Invalid scope", null));
        }
        return scopes;
    }

    private UserLogin refresh(String refreshTokenValue, GrantToken grant, RegisteredClient client) {
        Jwt refreshToken;
        try {
            refreshToken = refreshTokenDecoder.decode(refreshTokenValue);
        } catch (JwtValidationException e) {
            throw invalidGrant(e.getErrors().iterator().next().getDescription());
        } catch (JwtException e) {
            throw invalidGrant("Invalid refresh token");
        }
        if (!client.getClientId().equals(refreshToken.getClaimAsString(CLAIM_CLIENT_ID))) {
            throw invalidGrant("Refresh token was issued to another client");
        }
        List<String> loginScopes = refreshToken.getClaimAsStringList(OAuth2ParameterNames.SCOPE);
        Set<String> scopes = resolveScopes(grant, loginScopes != null ? new LinkedHashSet<>(loginScopes) : client.getScopes());
        UserDetails user = userService.loadUserByUsername(refreshToken.getClaimAsString(CLAIM_USER_NAME));
        if (!user.isEnabled()) {
            throw invalidGrant("User is disabled");
        }
        return new UserLogin(authenticated(user), new AuthorizationGrantType(refreshToken.getClaimAsString(CLAIM_GRANT_TYPE)), scopes);
    }

    private static Authentication authenticated(UserDetails user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    private static OAuth2AuthenticationException invalidGrant(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, description, null));
    }

    private UserLogin authenticateUserOrInvalidGrant(GrantToken grant, RegisteredClient client) {
        try {
            return authenticateUser(grant, client);
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (AuthenticationException | BadRequestException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, e.getMessage(), null), e);
        }
    }

    private record UserLogin(Authentication user, AuthorizationGrantType grantType, Set<String> scopes) {
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return GrantToken.class.isAssignableFrom(authentication);
    }

    public static class Converter implements AuthenticationConverter {
        @Override
        public Authentication convert(HttpServletRequest request) {
            String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
            if (grantType == null) {
                return null;
            }
            boolean userGrant = GRANT_TYPES.contains(grantType);
            boolean refreshGrant = GRANT_TYPE_REFRESH_TOKEN.equals(grantType);
            if (!userGrant && !refreshGrant) {
                return null;
            }
            Map<String, Object> parameters = new HashMap<>();
            request.getParameterMap().forEach((name, values) -> {
                if (!OAuth2ParameterNames.CLIENT_SECRET.equals(name) && values != null && values.length > 0) {
                    parameters.put(name, values[0]);
                }
            });
            if (userGrant && (!StringUtils.hasText((String) parameters.get(PARAMETER_USERNAME))
                    || !StringUtils.hasText((String) parameters.get(PARAMETER_PASSWORD)))) {
                throw invalidRequest(PARAMETER_USERNAME + " and " + PARAMETER_PASSWORD + " are required");
            }
            if (refreshGrant && !StringUtils.hasText((String) parameters.get(OAuth2ParameterNames.REFRESH_TOKEN))) {
                throw invalidRequest(OAuth2ParameterNames.REFRESH_TOKEN + " is required");
            }
            Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
            return new GrantToken(new AuthorizationGrantType(grantType), clientPrincipal, parameters);
        }

        private static OAuth2AuthenticationException invalidRequest(String description) {
            return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, description, null));
        }
    }

    public static class GrantToken extends OAuth2AuthorizationGrantAuthenticationToken {
        public GrantToken(AuthorizationGrantType grantType, Authentication clientPrincipal, Map<String, Object> parameters) {
            super(grantType, clientPrincipal, parameters);
        }

        public String parameter(String name) {
            Object value = getAdditionalParameters().get(name);
            return value instanceof String ? (String) value : null;
        }
    }
}

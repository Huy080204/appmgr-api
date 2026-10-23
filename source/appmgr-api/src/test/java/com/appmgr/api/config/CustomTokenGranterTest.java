package com.appmgr.api.config;

import com.appmgr.api.config.CustomTokenGranter.GrantToken;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomTokenGranterTest {
    private static final String CLIENT_ID = "abc_client";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserServiceImpl userService;

    @Mock
    private OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    @Mock
    private JwtDecoder refreshTokenDecoder;

    @BeforeEach
    void setUp() {
        AuthorizationServerContextHolder.setContext(mock(AuthorizationServerContext.class));
    }

    @AfterEach
    void tearDown() {
        AuthorizationServerContextHolder.resetContext();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldIssueAccessAndRefreshTokenWhenPasswordGrantIsValid() {
        RegisteredClient client = client("password", "refresh_token");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("password", client, passwordParams()));

        assertThat(result.getAccessToken().getTokenValue()).isEqualTo("access-value");
        assertThat(result.getAccessToken().getScopes()).containsExactly("read", "write");
        assertThat(result.getRefreshToken().getTokenValue()).isEqualTo("refresh-value");
    }

    @Test
    void shouldPassLoginGrantUserAndScopesToTokenContextWhenPasswordGrantIsValid() {
        RegisteredClient client = client("password", "refresh_token");
        Authentication user = authenticatedUser();
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(user);
        stubTokenGenerator();

        authenticate(grantToken("password", client, passwordParams()));

        ArgumentCaptor<OAuth2TokenContext> context = ArgumentCaptor.forClass(OAuth2TokenContext.class);
        verify(tokenGenerator, times(2)).generate(context.capture());
        OAuth2TokenContext accessContext = context.getAllValues().get(0);
        assertThat(accessContext.getTokenType()).isEqualTo(OAuth2TokenType.ACCESS_TOKEN);
        assertThat(accessContext.getAuthorizationGrantType().getValue()).isEqualTo("password");
        assertThat(accessContext.<Authentication>getPrincipal()).isSameAs(user);
        assertThat(accessContext.getAuthorizedScopes()).containsExactly("read", "write");
        assertThat(context.getAllValues().get(1).getTokenType()).isEqualTo(OAuth2TokenType.REFRESH_TOKEN);
    }

    @Test
    void shouldNotIssueRefreshTokenWhenClientLacksRefreshTokenGrant() {
        RegisteredClient client = client("password");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("password", client, passwordParams()));

        assertThat(result.getRefreshToken()).isNull();
        verify(tokenGenerator, times(1)).generate(any(OAuth2TokenContext.class));
    }

    @Test
    void shouldNarrowScopesWhenPasswordRequestsSubsetOfClientScopes() {
        RegisteredClient client = client("password");
        Map<String, Object> params = passwordParams();
        params.put("scope", "read");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("password", client, params));

        assertThat(result.getAccessToken().getScopes()).containsExactly("read");
    }

    @Test
    void shouldRaiseInvalidScopeBeforeVerifyingPasswordWhenScopeIsOutsideClient() {
        RegisteredClient client = client("password");
        Map<String, Object> params = passwordParams();
        params.put("scope", "read admin");

        assertThatThrownBy(() -> authenticate(grantToken("password", client, params)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_scope"));
        verifyNoInteractions(authenticationManager, tokenGenerator);
    }

    @Test
    void shouldRaiseInvalidGrantWhenPasswordIsWrong() {
        RegisteredClient client = client("password");
        when(authenticationManager.authenticate(any(Authentication.class))).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authenticate(grantToken("password", client, passwordParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("Bad credentials");
                });
        verifyNoInteractions(tokenGenerator);
    }

    @Test
    void shouldVerifyUserWithOtpAndKeepLoginGrantWhenGrantIsPasswordMfa() {
        RegisteredClient client = client("password_mfa");
        Map<String, Object> params = passwordParams();
        params.put("otp", "123456");
        when(userService.loadUserForPasswordOtp("admin", "secret", "123456")).thenReturn(userDetails(true));
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("password_mfa", client, params));

        ArgumentCaptor<OAuth2TokenContext> context = ArgumentCaptor.forClass(OAuth2TokenContext.class);
        verify(tokenGenerator).generate(context.capture());
        assertThat(context.getValue().getAuthorizationGrantType().getValue()).isEqualTo("password_mfa");
        assertThat(result.getAccessToken().getScopes()).containsExactly("read", "write");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void shouldNarrowScopesWhenPasswordMfaRequestsSubsetOfClientScopes() {
        RegisteredClient client = client("password_mfa");
        Map<String, Object> params = passwordParams();
        params.put("otp", "123456");
        params.put("scope", "write");
        when(userService.loadUserForPasswordOtp("admin", "secret", "123456")).thenReturn(userDetails(true));
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("password_mfa", client, params));

        assertThat(result.getAccessToken().getScopes()).containsExactly("write");
    }

    @Test
    void shouldRaiseInvalidScopeBeforeVerifyingOtpWhenPasswordMfaScopeIsOutsideClient() {
        RegisteredClient client = client("password_mfa");
        Map<String, Object> params = passwordParams();
        params.put("otp", "123456");
        params.put("scope", "admin");

        assertThatThrownBy(() -> authenticate(grantToken("password_mfa", client, params)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_scope"));
        verifyNoInteractions(userService, tokenGenerator);
    }

    @Test
    void shouldRaiseInvalidGrantWhenOtpIsWrong() {
        RegisteredClient client = client("password_mfa");
        Map<String, Object> params = passwordParams();
        params.put("otp", "000000");
        when(userService.loadUserForPasswordOtp("admin", "secret", "000000")).thenThrow(new BadRequestException("Verified Fail"));

        assertThatThrownBy(() -> authenticate(grantToken("password_mfa", client, params)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("Verified Fail");
                });
    }

    @Test
    void shouldRaiseInvalidGrantWhenPasswordMfaUserIsUnknown() {
        RegisteredClient client = client("password_mfa");
        Map<String, Object> params = passwordParams();
        params.put("otp", "123456");
        when(userService.loadUserForPasswordOtp("admin", "secret", "123456"))
                .thenThrow(new UsernameNotFoundException("Invalid username or password!"));

        assertThatThrownBy(() -> authenticate(grantToken("password_mfa", client, params)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant"));
    }

    @Test
    void shouldRaiseUnauthorizedClientWhenClientLacksTheGrant() {
        RegisteredClient client = client("client_credentials");

        assertThatThrownBy(() -> authenticate(grantToken("password", client, passwordParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("unauthorized_client"));
        verifyNoInteractions(authenticationManager, tokenGenerator);
    }

    @Test
    void shouldRaiseInvalidClientWhenPrincipalIsNotAClientToken() {
        GrantToken grant = new GrantToken(new AuthorizationGrantType("password"), new TestingAuthenticationToken("x", "y"), passwordParams());

        assertThatThrownBy(() -> granter().authenticate(grant))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_client"));
    }

    @Test
    void shouldRaiseInvalidClientWhenClientTokenIsNotAuthenticated() {
        OAuth2ClientAuthenticationToken unauthenticated = new OAuth2ClientAuthenticationToken(
                CLIENT_ID, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret", null);
        GrantToken grant = new GrantToken(new AuthorizationGrantType("password"), unauthenticated, passwordParams());

        assertThatThrownBy(() -> granter().authenticate(grant))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_client"));
    }

    @Test
    void shouldRaiseUnsupportedGrantTypeWhenGrantIsNotOneThisGranterHandles() {
        RegisteredClient client = client("client_credentials");

        assertThatThrownBy(() -> authenticate(grantToken("client_credentials", client, new HashMap<>())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("unsupported_grant_type"));
    }

    @Test
    void shouldRaiseServerErrorWhenAccessTokenCannotBeGenerated() {
        RegisteredClient client = client("password");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        when(tokenGenerator.generate(any(OAuth2TokenContext.class))).thenReturn(null);

        assertThatThrownBy(() -> authenticate(grantToken("password", client, passwordParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("server_error"));
    }

    @Test
    void shouldRaiseServerErrorWhenRefreshTokenCannotBeGenerated() {
        RegisteredClient client = client("password", "refresh_token");
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        when(tokenGenerator.generate(any(OAuth2TokenContext.class))).thenReturn(accessToken()).thenReturn(null);

        assertThatThrownBy(() -> authenticate(grantToken("password", client, passwordParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("server_error"));
    }

    @Test
    void shouldSupportOnlyGrantTokens() {
        CustomTokenGranter granter = granter();

        assertThat(granter.supports(GrantToken.class)).isTrue();
        assertThat(granter.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    @Test
    void shouldReloadUserAndKeepLoginGrantAndScopesWhenRefreshing() {
        RegisteredClient client = client("password_mfa", "refresh_token");
        UserDetails reloaded = userDetails(true);
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt(CLIENT_ID, "password_mfa", List.of("read")));
        when(userService.loadUserByUsername("admin")).thenReturn(reloaded);
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("refresh_token", client, refreshParams()));

        ArgumentCaptor<OAuth2TokenContext> context = ArgumentCaptor.forClass(OAuth2TokenContext.class);
        verify(tokenGenerator, times(2)).generate(context.capture());
        OAuth2TokenContext accessContext = context.getAllValues().get(0);
        assertThat(accessContext.getAuthorizationGrantType().getValue()).isEqualTo("password_mfa");
        assertThat(accessContext.<Authentication>getPrincipal().getPrincipal()).isSameAs(reloaded);
        assertThat(accessContext.getAuthorizedScopes()).containsExactly("read");
        assertThat(result.getAccessToken().getScopes()).containsExactly("read");
    }

    @Test
    void shouldKeepClientScopesWhenRefreshTokenHasNoScopeClaim() {
        RegisteredClient client = client("password", "refresh_token");
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt(CLIENT_ID, "password", null));
        when(userService.loadUserByUsername("admin")).thenReturn(userDetails(true));
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("refresh_token", client, refreshParams()));

        assertThat(result.getAccessToken().getScopes()).containsExactly("read", "write");
    }

    @Test
    void shouldNarrowLoginScopesWhenRefreshRequestsSubset() {
        RegisteredClient client = client("password", "refresh_token");
        Map<String, Object> params = refreshParams();
        params.put("scope", "write");
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt(CLIENT_ID, "password", List.of("read", "write")));
        when(userService.loadUserByUsername("admin")).thenReturn(userDetails(true));
        stubTokenGenerator();

        OAuth2AccessTokenAuthenticationToken result = authenticate(grantToken("refresh_token", client, params));

        assertThat(result.getAccessToken().getScopes()).containsExactly("write");
    }

    @Test
    void shouldRaiseInvalidScopeWhenRefreshRequestsWiderScopeThanLogin() {
        RegisteredClient client = client("password", "refresh_token");
        Map<String, Object> params = refreshParams();
        params.put("scope", "read write");
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt(CLIENT_ID, "password", List.of("read")));

        assertThatThrownBy(() -> authenticate(grantToken("refresh_token", client, params)))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_scope"));
        verifyNoInteractions(userService, tokenGenerator);
    }

    @Test
    void shouldRaiseInvalidGrantWhenRefreshTokenWasIssuedToAnotherClient() {
        RegisteredClient client = client("password", "refresh_token");
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt("other_client", "password", List.of("read")));

        assertThatThrownBy(() -> authenticate(grantToken("refresh_token", client, refreshParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("Refresh token was issued to another client");
                });
    }

    @Test
    void shouldKeepValidatorMessageWhenRefreshJwtIsRejected() {
        RegisteredClient client = client("password", "refresh_token");
        OAuth2Error forceExpired = new OAuth2Error("invalid_token", "Token has been force expired (version mismatch)", null);
        when(refreshTokenDecoder.decode("refresh-in")).thenThrow(new JwtValidationException("rejected", List.of(forceExpired)));

        assertThatThrownBy(() -> authenticate(grantToken("refresh_token", client, refreshParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("Token has been force expired (version mismatch)");
                });
    }

    @Test
    void shouldRaiseInvalidGrantWhenRefreshTokenCannotBeDecoded() {
        RegisteredClient client = client("password", "refresh_token");
        when(refreshTokenDecoder.decode("refresh-in")).thenThrow(new BadJwtException("garbage"));

        assertThatThrownBy(() -> authenticate(grantToken("refresh_token", client, refreshParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("Invalid refresh token");
                });
    }

    @Test
    void shouldRaiseInvalidGrantWhenUserIsLockedOnRefresh() {
        RegisteredClient client = client("password", "refresh_token");
        when(refreshTokenDecoder.decode("refresh-in")).thenReturn(refreshJwt(CLIENT_ID, "password", List.of("read")));
        when(userService.loadUserByUsername("admin")).thenReturn(userDetails(false));

        assertThatThrownBy(() -> authenticate(grantToken("refresh_token", client, refreshParams())))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("User is disabled");
                });
        verifyNoInteractions(tokenGenerator);
    }

    @Test
    void shouldConvertPasswordRequestWithoutClientSecret() {
        Authentication clientPrincipal = clientPrincipal(client("password"));
        SecurityContextHolder.getContext().setAuthentication(clientPrincipal);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "password");
        request.addParameter("username", "admin");
        request.addParameter("password", "secret");
        request.addParameter("client_secret", "shh");

        GrantToken grant = (GrantToken) new CustomTokenGranter.Converter().convert(request);

        assertThat(grant.getGrantType().getValue()).isEqualTo("password");
        assertThat(grant.getPrincipal()).isSameAs(clientPrincipal);
        assertThat(grant.parameter("username")).isEqualTo("admin");
        assertThat(grant.parameter("password")).isEqualTo("secret");
        assertThat(grant.getAdditionalParameters()).doesNotContainKey("client_secret");
    }

    @Test
    void shouldConvertRefreshTokenRequest() {
        SecurityContextHolder.getContext().setAuthentication(clientPrincipal(client("refresh_token")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "refresh_token");
        request.addParameter("refresh_token", "refresh-in");

        GrantToken grant = (GrantToken) new CustomTokenGranter.Converter().convert(request);

        assertThat(grant.getGrantType().getValue()).isEqualTo("refresh_token");
        assertThat(grant.parameter("refresh_token")).isEqualTo("refresh-in");
    }

    @Test
    void shouldLeaveOtherGrantsToSpringAuthorizationServerWhenConverting() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "client_credentials");

        assertThat(new CustomTokenGranter.Converter().convert(request)).isNull();
    }

    @Test
    void shouldReturnNullWhenRequestHasNoGrantType() {
        assertThat(new CustomTokenGranter.Converter().convert(new MockHttpServletRequest())).isNull();
    }

    @Test
    void shouldRaiseInvalidRequestWhenPasswordRequestHasNoUsername() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "password");
        request.addParameter("password", "secret");

        assertThatThrownBy(() -> new CustomTokenGranter.Converter().convert(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_request"));
    }

    @Test
    void shouldRaiseInvalidRequestWhenPasswordMfaRequestHasNoPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "password_mfa");
        request.addParameter("username", "admin");

        assertThatThrownBy(() -> new CustomTokenGranter.Converter().convert(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_request"));
    }

    @Test
    void shouldRaiseInvalidRequestWhenRefreshRequestHasNoRefreshToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("grant_type", "refresh_token");

        assertThatThrownBy(() -> new CustomTokenGranter.Converter().convert(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_request"));
    }

    private CustomTokenGranter granter() {
        return new CustomTokenGranter(authenticationManager, userService, tokenGenerator, refreshTokenDecoder);
    }

    private OAuth2AccessTokenAuthenticationToken authenticate(GrantToken grant) {
        return (OAuth2AccessTokenAuthenticationToken) granter().authenticate(grant);
    }

    private static RegisteredClient client(String... grantTypes) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .scope("read")
                .scope("write");
        for (String grantType : grantTypes) {
            builder.authorizationGrantType(new AuthorizationGrantType(grantType));
        }
        return builder.build();
    }

    private static Authentication clientPrincipal(RegisteredClient client) {
        return new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret");
    }

    private static GrantToken grantToken(String grantType, RegisteredClient client, Map<String, Object> params) {
        return new GrantToken(new AuthorizationGrantType(grantType), clientPrincipal(client), params);
    }

    private static Map<String, Object> passwordParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("username", "admin");
        params.put("password", "secret");
        return params;
    }

    private static Map<String, Object> refreshParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("refresh_token", "refresh-in");
        return params;
    }

    private static UserDetails userDetails(boolean enabled) {
        return new User("admin", "n/a", enabled, true, true, true, List.of(new SimpleGrantedAuthority("ROLE_ACC_C")));
    }

    private static Authentication authenticatedUser() {
        UserDetails user = userDetails(true);
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    private static Jwt refreshJwt(String clientId, String loginGrant, List<String> scopes) {
        Jwt.Builder builder = Jwt.withTokenValue("refresh-in")
                .header("alg", "HS256")
                .claim("token_type", "refresh")
                .claim("user_name", "admin")
                .claim("client_id", clientId)
                .claim("grant_type", loginGrant);
        if (scopes != null) {
            builder.claim("scope", scopes);
        }
        return builder.build();
    }

    private static Jwt accessToken() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-value").header("alg", "HS256").claim("sub", "admin")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
    }

    private void stubTokenGenerator() {
        when(tokenGenerator.generate(any(OAuth2TokenContext.class))).thenAnswer(invocation -> {
            OAuth2TokenContext context = invocation.getArgument(0);
            if (OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
                Instant now = Instant.now();
                return new OAuth2RefreshToken("refresh-value", now, now.plusSeconds(60));
            }
            return accessToken();
        });
    }
}

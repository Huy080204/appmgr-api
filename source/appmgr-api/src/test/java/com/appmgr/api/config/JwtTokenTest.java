package com.appmgr.api.config;

import com.appmgr.api.config.security.CustomJwtAuthenticationConverter;
import com.appmgr.api.dto.AccountForTokenDto;
import com.appmgr.api.jwt.BaseJwt;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenTest {
    private static final String SIGNING_KEY = "0123456789abcdef0123456789abcdef";
    private static final String USERNAME = "admin";
    private static final int VERSION = 3;

    @Test
    void shouldFailFastWhenSigningKeyIsShorterThan32Bytes() {
        JwtConfig config = jwtConfig("T7PfKrPGvK", VERSION);

        assertThatThrownBy(config::jwtSigningKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes")
                .hasMessageContaining("got 10");
    }

    @Test
    void shouldSignAccessTokenWithHs256AndJwtTypeHeader() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);

        Jwt token = decode(config.jwtDecoder(config.jwtSigningKey()), issueAccessToken(config, VERSION, AuthorizationGrantType.PASSWORD));

        assertThat(token.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
    }

    @Test
    void shouldWriteLegacyClaimsWhenGrantIsPassword() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);

        Jwt token = decode(config.jwtDecoder(config.jwtSigningKey()), issueAccessToken(config, VERSION, AuthorizationGrantType.PASSWORD));

        assertThat(token.getClaimAsString("user_name")).isEqualTo(USERNAME);
        assertThat(token.getClaimAsString("client_id")).isEqualTo("abc_client");
        assertThat(token.getClaimAsString("grant_type")).isEqualTo("password");
        assertThat(token.<Number>getClaim("version").intValue()).isEqualTo(VERSION);
        assertThat(token.<Number>getClaim("user_id").longValue()).isEqualTo(77L);
        assertThat(token.<Number>getClaim("user_kind").intValue()).isEqualTo(1);
        assertThat(token.getClaimAsStringList("authorities")).containsExactly("ROLE_ACC_C");
        assertThat(token.getClaimAsStringList("scope")).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void shouldWriteAdditionalInfoThatBaseJwtDecodesWhenGrantIsPassword() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);

        Jwt token = issueAccessToken(config, VERSION, AuthorizationGrantType.PASSWORD);

        BaseJwt additionalInfo = BaseJwt.decode(token.getClaimAsString("additional_info"));
        assertThat(additionalInfo.getAccountId()).isEqualTo(77L);
        assertThat(additionalInfo.getUsername()).isEqualTo(USERNAME);
        assertThat(additionalInfo.getIsSuperAdmin()).isTrue();
    }

    @Test
    void shouldWritePasswordMfaGrantTypeClaimWhenGrantIsPasswordMfa() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);

        Jwt token = issueAccessToken(config, VERSION, new AuthorizationGrantType("password_mfa"));

        assertThat(token.getClaimAsString("grant_type")).isEqualTo("password_mfa");
        assertThat(token.getClaimAsStringList("authorities")).containsExactly("ROLE_ACC_C");
    }

    @Test
    void shouldWriteVersionAndClientIdButNoAuthoritiesWhenGrantIsClientCredentials() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);

        Jwt token = decode(config.jwtDecoder(config.jwtSigningKey()), issueAccessToken(config, VERSION, AuthorizationGrantType.CLIENT_CREDENTIALS));

        assertThat(token.<Number>getClaim("version").intValue()).isEqualTo(VERSION);
        assertThat(token.getClaimAsString("client_id")).isEqualTo("abc_client");
        assertThat(token.hasClaim("authorities")).isFalse();
        assertThat(token.hasClaim("user_name")).isFalse();
    }

    @Test
    void shouldWriteNoUserClaimsWhenAccountDoesNotExist() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(USERNAME))).thenReturn(List.of());

        Jwt token = issueAccessToken(config, enhancer(jdbcTemplate, VERSION), AuthorizationGrantType.PASSWORD);

        assertThat(token.hasClaim("authorities")).isFalse();
        assertThat(token.hasClaim("additional_info")).isFalse();
        assertThat(token.<Number>getClaim("version").intValue()).isEqualTo(VERSION);
    }

    @Test
    void shouldMapAuthoritiesWithoutPrefixAndNameFromUserNameWhenConvertingToken() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        Jwt token = decode(config.jwtDecoder(config.jwtSigningKey()), issueAccessToken(config, VERSION, AuthorizationGrantType.PASSWORD));

        AbstractAuthenticationToken authentication = new CustomJwtAuthenticationConverter().convert(token);

        assertThat(authentication.getName()).isEqualTo(USERNAME);
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ACC_C");
    }

    @Test
    void shouldNameClientAndGrantNoAuthoritiesWhenConvertingClientCredentialsToken() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        Jwt token = decode(config.jwtDecoder(config.jwtSigningKey()), issueAccessToken(config, VERSION, AuthorizationGrantType.CLIENT_CREDENTIALS));

        AbstractAuthenticationToken authentication = new CustomJwtAuthenticationConverter().convert(token);

        assertThat(authentication.getName()).isEqualTo("abc_client");
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void shouldNameSubjectWhenTokenHasNeitherUserNameNorClientId() {
        Jwt token = Jwt.withTokenValue("t").header("alg", "HS256").subject("subject-only").claim("x", "y").build();

        AbstractAuthenticationToken authentication = new CustomJwtAuthenticationConverter().convert(token);

        assertThat(authentication.getName()).isEqualTo("subject-only");
    }

    @Test
    void shouldRejectAccessTokenWhenPasswordLoginVersionIsOlder() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, 1);
        JwtConfig verifier = jwtConfig(SIGNING_KEY, 2);
        Jwt token = issueAccessToken(issuer, 1, AuthorizationGrantType.PASSWORD);

        assertThatThrownBy(() -> verifier.jwtDecoder(verifier.jwtSigningKey()).decode(token.getTokenValue()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Token has been force expired (version mismatch)");
    }

    @Test
    void shouldAcceptAccessTokenWhenVersionIsNewerThanCurrent() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, 5);
        JwtConfig verifier = jwtConfig(SIGNING_KEY, 2);
        Jwt token = issueAccessToken(issuer, 5, AuthorizationGrantType.PASSWORD);

        Jwt decoded = verifier.jwtDecoder(verifier.jwtSigningKey()).decode(token.getTokenValue());

        assertThat(decoded.getClaimAsString("user_name")).isEqualTo(USERNAME);
    }

    @Test
    void shouldAcceptOlderVersionWhenGrantIsPasswordMfa() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, 1);
        JwtConfig verifier = jwtConfig(SIGNING_KEY, 2);
        Jwt token = issueAccessToken(issuer, 1, new AuthorizationGrantType("password_mfa"));

        Jwt decoded = verifier.jwtDecoder(verifier.jwtSigningKey()).decode(token.getTokenValue());

        assertThat(decoded.getClaimAsString("grant_type")).isEqualTo("password_mfa");
    }

    @Test
    void shouldAcceptOlderVersionWhenTokenIsClientCredentials() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, 1);
        JwtConfig verifier = jwtConfig(SIGNING_KEY, 2);
        Jwt token = issueAccessToken(issuer, 1, AuthorizationGrantType.CLIENT_CREDENTIALS);

        Jwt decoded = verifier.jwtDecoder(verifier.jwtSigningKey()).decode(token.getTokenValue());

        assertThat(decoded.getClaimAsString("client_id")).isEqualTo("abc_client");
    }

    @Test
    void shouldRejectAccessTokenWhenVersionClaimIsMissingOnPasswordLogin() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        SecretKey key = config.jwtSigningKey();
        JwtEncoder encoder = config.jwtEncoder(config.jwkSource(key));
        String token = encodeRaw(encoder, "grant_type", "password", null);

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("force expired");
    }

    @Test
    void shouldRejectTokenWhenSignedWithAnotherKey() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, VERSION);
        JwtConfig verifier = jwtConfig("fedcba9876543210fedcba9876543210", VERSION);
        Jwt token = issueAccessToken(issuer, VERSION, AuthorizationGrantType.PASSWORD);

        assertThatThrownBy(() -> verifier.jwtDecoder(verifier.jwtSigningKey()).decode(token.getTokenValue()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldReadRefreshTokenWithItsClaimsWhenDecodedByRefreshDecoder() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        OAuth2RefreshToken refreshToken = issueRefreshToken(config, VERSION, AuthorizationGrantType.PASSWORD);

        Jwt decoded = config.refreshTokenDecoder(config.jwtSigningKey()).decode(refreshToken.getTokenValue());

        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(decoded.getClaimAsString("token_type")).isEqualTo("refresh");
        assertThat(decoded.getClaimAsString("user_name")).isEqualTo(USERNAME);
        assertThat(decoded.getClaimAsString("client_id")).isEqualTo("abc_client");
        assertThat(decoded.getClaimAsString("grant_type")).isEqualTo("password");
        assertThat(decoded.<Number>getClaim("version").intValue()).isEqualTo(VERSION);
        assertThat(decoded.getClaimAsStringList("scope")).containsExactly("read", "write");
    }

    @Test
    void shouldRejectRefreshTokenWhenUsedAsBearer() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        OAuth2RefreshToken refreshToken = issueRefreshToken(config, VERSION, AuthorizationGrantType.PASSWORD);

        assertThatThrownBy(() -> config.jwtDecoder(config.jwtSigningKey()).decode(refreshToken.getTokenValue()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("A refresh token cannot be used as an access token");
    }

    @Test
    void shouldRejectAccessTokenWhenUsedAsRefreshToken() {
        JwtConfig config = jwtConfig(SIGNING_KEY, VERSION);
        Jwt accessToken = issueAccessToken(config, VERSION, AuthorizationGrantType.PASSWORD);

        assertThatThrownBy(() -> config.refreshTokenDecoder(config.jwtSigningKey()).decode(accessToken.getTokenValue()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Not a refresh token");
    }

    @Test
    void shouldRejectRefreshTokenWhenPasswordLoginVersionIsOlder() {
        JwtConfig issuer = jwtConfig(SIGNING_KEY, 1);
        JwtConfig verifier = jwtConfig(SIGNING_KEY, 2);
        OAuth2RefreshToken refreshToken = issueRefreshToken(issuer, 1, AuthorizationGrantType.PASSWORD);

        assertThatThrownBy(() -> verifier.refreshTokenDecoder(verifier.jwtSigningKey()).decode(refreshToken.getTokenValue()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Token has been force expired (version mismatch)");
    }

    private static JwtConfig jwtConfig(String signingKey, int version) {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "signingKey", signingKey);
        ReflectionTestUtils.setField(config, "currentVersion", version);
        return config;
    }

    private static CustomTokenEnhancer enhancer(JdbcTemplate jdbcTemplate, int version) {
        CustomTokenEnhancer enhancer = new CustomTokenEnhancer();
        ReflectionTestUtils.setField(enhancer, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(enhancer, "currentVersion", version);
        return enhancer;
    }

    private static CustomTokenEnhancer enhancerWithAccount(int version) {
        AccountForTokenDto account = new AccountForTokenDto();
        account.setId(77L);
        account.setKind(1);
        account.setUsername(USERNAME);
        account.setIsSuperAdmin(true);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(USERNAME))).thenReturn(List.of(account));
        return enhancer(jdbcTemplate, version);
    }

    private static Jwt issueAccessToken(JwtConfig config, int version, AuthorizationGrantType grantType) {
        return issueAccessToken(config, enhancerWithAccount(version), grantType);
    }

    private static Jwt issueAccessToken(JwtConfig config, CustomTokenEnhancer enhancer, AuthorizationGrantType grantType) {
        JwtGenerator generator = new JwtGenerator(config.jwtEncoder(config.jwkSource(config.jwtSigningKey())));
        generator.setJwtCustomizer(enhancer);
        return generator.generate(tokenContext(OAuth2TokenType.ACCESS_TOKEN, grantType));
    }

    private static OAuth2RefreshToken issueRefreshToken(JwtConfig config, int version, AuthorizationGrantType grantType) {
        AuthorizationServerConfig serverConfig = new AuthorizationServerConfig();
        ReflectionTestUtils.setField(serverConfig, "currentVersion", version);
        JwtEncoder encoder = config.jwtEncoder(config.jwkSource(config.jwtSigningKey()));
        return serverConfig.refreshTokenGenerator(encoder).generate(tokenContext(OAuth2TokenType.REFRESH_TOKEN, grantType));
    }

    private static OAuth2TokenContext tokenContext(OAuth2TokenType tokenType, AuthorizationGrantType grantType) {
        AuthorizationServerContext serverContext = mock(AuthorizationServerContext.class);
        when(serverContext.getIssuer()).thenReturn("http://localhost");
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("abc_client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(grantType)
                .scope("read")
                .scope("write")
                .build();
        UsernamePasswordAuthenticationToken user = UsernamePasswordAuthenticationToken.authenticated(
                new User(USERNAME, "n/a", List.of(new SimpleGrantedAuthority("ROLE_ACC_C"))), null,
                List.of(new SimpleGrantedAuthority("ROLE_ACC_C")));
        return DefaultOAuth2TokenContext.builder()
                .registeredClient(client)
                .principal(AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grantType) ? new TestingAuthenticationToken("abc_client", null) : user)
                .authorizationServerContext(serverContext)
                .authorizedScopes(client.getScopes())
                .tokenType(tokenType)
                .authorizationGrantType(grantType)
                .build();
    }

    private static Jwt decode(JwtDecoder decoder, Jwt token) {
        return decoder.decode(token.getTokenValue());
    }

    private static String encodeRaw(JwtEncoder encoder, String claimName, String claimValue, Integer version) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(USERNAME)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim(claimName, claimValue);
        if (version != null) {
            claims.claim("version", version);
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}

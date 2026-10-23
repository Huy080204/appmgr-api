package com.appmgr.api.config;

import com.appmgr.api.config.security.CustomOAuth2AuthEntryPoint;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.appmgr.api.service.impl.UserServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AccessTokenResponseAuthenticationSuccessHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.appmgr.api.config.SecurityConstant.CLAIM_ADDITIONAL_INFO;
import static com.appmgr.api.config.SecurityConstant.CLAIM_CLIENT_ID;
import static com.appmgr.api.config.SecurityConstant.CLAIM_GRANT_TYPE;
import static com.appmgr.api.config.SecurityConstant.CLAIM_TOKEN_TYPE;
import static com.appmgr.api.config.SecurityConstant.CLAIM_USER_NAME;
import static com.appmgr.api.config.SecurityConstant.CLAIM_VERSION;
import static com.appmgr.api.config.SecurityConstant.JWT_HEADER_TYPE;
import static com.appmgr.api.config.SecurityConstant.TOKEN_ENDPOINT;
import static com.appmgr.api.config.SecurityConstant.TOKEN_TYPE_REFRESH;

@Slf4j
@Configuration
public class AuthorizationServerConfig {
    private static final List<String> LEGACY_RESPONSE_FIELDS = Arrays.asList(
            "user_id", "user_kind", CLAIM_GRANT_TYPE, CLAIM_VERSION, CLAIM_ADDITIONAL_INFO, "jti");

    @Value("${jwt.version}")
    private Integer currentVersion;

    @Autowired
    private CustomOAuth2AuthEntryPoint customOAuth2AuthEntryPoint;

    @Autowired
    private ObjectMapper objectMapper;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            AuthenticationManager authenticationManager, UserServiceImpl userService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshTokenDecoder) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        AuthenticationFailureHandler errorResponseHandler = this::writeTokenError;
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .tokenGenerator(tokenGenerator)
                        .clientAuthentication(client -> client.errorResponseHandler(errorResponseHandler))
                        .tokenEndpoint(token -> token
                                .accessTokenRequestConverter(new CustomTokenGranter.Converter())
                                .authenticationProvider(new CustomTokenGranter(
                                        authenticationManager, userService, tokenGenerator, refreshTokenDecoder))
                                .accessTokenResponseHandler(accessTokenResponseHandler())
                                .errorResponseHandler(errorResponseHandler)))
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customOAuth2AuthEntryPoint));
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().tokenEndpoint(TOKEN_ENDPOINT).build();
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JwtEncoder jwtEncoder,
            OAuth2TokenCustomizer<JwtEncodingContext> tokenEnhancer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenEnhancer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, refreshTokenGenerator(jwtEncoder));
    }

    OAuth2TokenGenerator<OAuth2RefreshToken> refreshTokenGenerator(JwtEncoder jwtEncoder) {
        return context -> {
            if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
                return null;
            }
            Instant issuedAt = Instant.now();
            String username = context.getPrincipal().getName();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .id(UUID.randomUUID().toString()).subject(username).issuedAt(issuedAt)
                    .expiresAt(issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive()))
                    .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                    .claim(CLAIM_USER_NAME, username)
                    .claim(CLAIM_CLIENT_ID, context.getRegisteredClient().getClientId())
                    .claim(CLAIM_GRANT_TYPE, context.getAuthorizationGrantType().getValue())
                    .claim(CLAIM_VERSION, currentVersion)
                    .claim(OAuth2ParameterNames.SCOPE, context.getAuthorizedScopes())
                    .build();
            Jwt jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type(JWT_HEADER_TYPE).build(), claims));
            return new OAuth2RefreshToken(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt());
        };
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService() {
        return new NoOpAuthorizationService();
    }

    private AuthenticationSuccessHandler accessTokenResponseHandler() {
        OAuth2AccessTokenResponseAuthenticationSuccessHandler handler = new OAuth2AccessTokenResponseAuthenticationSuccessHandler();
        handler.setAccessTokenResponseCustomizer(this::addLegacyResponseFields);
        return handler;
    }

    private void addLegacyResponseFields(OAuth2AccessTokenAuthenticationContext context) {
        OAuth2AccessTokenAuthenticationToken accessTokenAuthentication = context.getAuthentication();
        Map<String, Object> additionalParameters = new HashMap<>();
        try {
            Map<String, Object> claims = SignedJWT.parse(accessTokenAuthentication.getAccessToken().getTokenValue())
                    .getJWTClaimsSet().getClaims();
            LEGACY_RESPONSE_FIELDS.forEach(field -> {
                if (claims.get(field) != null) {
                    additionalParameters.put(field, claims.get(field));
                }
            });
        } catch (ParseException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, "Cannot read the access token", null), e);
        }
        OAuth2AccessTokenResponse.Builder response = context.getAccessTokenResponse();
        response.additionalParameters(additionalParameters);
    }

    private void writeTokenError(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        OAuth2Error error = exception instanceof OAuth2AuthenticationException
                ? ((OAuth2AuthenticationException) exception).getError()
                : new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, exception.getMessage(), null);
        HttpStatus status = statusOf(error, exception);
        String message = error.getDescription() != null ? error.getDescription() : error.getErrorCode();

        Map<String, Object> body = new HashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        body.put("data", Arrays.asList(error.getErrorCode(), message));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static HttpStatus statusOf(OAuth2Error error, AuthenticationException exception) {
        if (exception.getCause() instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception.getCause() instanceof BadRequestException) {
            return HttpStatus.BAD_REQUEST;
        }
        switch (error.getErrorCode()) {
            case OAuth2ErrorCodes.INVALID_CLIENT:
                return HttpStatus.UNAUTHORIZED;
            case OAuth2ErrorCodes.SERVER_ERROR:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
}

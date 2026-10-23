package com.appmgr.api.config;

import com.appmgr.api.config.security.CustomJwtAuthenticationConverter;
import com.appmgr.api.config.security.CustomOAuth2AuthEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_PATHS = {
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/error",
            SecurityConstant.TOKEN_ENDPOINT, "/actuator/health", "/actuator/health/**",
            "/v1/account/login",
            "/v1/setting/find-by-key", "/v1/setting/find-by-group", "/v1/setting/public",
            "/v1/file/download/**",
            "/v1/*/public/**",
            "/v1/bundle/check-version", "/v1/bundle/download-version",
            "/v1/ota/manifest", "/v1/ota/assets/**"
    };

    @Autowired
    private CustomOAuth2AuthEntryPoint customOAuth2AuthEntryPoint;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(new CustomJwtAuthenticationConverter()))
                        .authenticationEntryPoint(customOAuth2AuthEntryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customOAuth2AuthEntryPoint)
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("HEAD", "GET", "POST", "PUT", "OPTIONS", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of(
                "Accept", "Origin", "Content-Type", "Depth", "User-Agent", "If-Modified-Since",
                "Cache-Control", "Authorization", "X-Req", "X-File-Size", "X-Requested-With", "X-File-Name", "Content-Disposition",
                "Access-Control-Allow-Headers", "Access-Control-Request-Method", "Access-Control-Request-Headers",
                "X-Client-Type", "x-app-id", "x-category-id",
                "expo-channel-name", "expo-platform", "expo-runtime-version",
                "expo-current-update-id", "expo-embedded-update-id", "expo-protocol-version",
                "expo-expect-signature", "expo-recent-failed-update-ids", "expo-extra-params",
                "expo-json-error", "expo-api-version"));
        config.setExposedHeaders(List.of(
                "Origin", "X-Requested-With", "Content-Type", "Accept", "Authorization", "Content-Disposition",
                "expo-protocol-version", "expo-sfv-version", "cache-control"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

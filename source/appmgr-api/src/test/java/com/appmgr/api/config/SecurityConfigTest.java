package com.appmgr.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    private final CorsConfigurationSource source = new SecurityConfig().corsConfigurationSource();

    @Test
    void shouldAllowPreflightWithMaxAgeWhenHeaderAndMethodAreConfigured() throws IOException {
        MockHttpServletRequest request = preflight("PATCH", "authorization, x-client-type, content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();

        process(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://app.example.com");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PATCH");
    }

    @Test
    void shouldAllowPreflightForExpoUpdateHeaders() throws IOException {
        MockHttpServletRequest request = preflight("GET", "expo-protocol-version, expo-platform, expo-expect-signature, x-app-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        process(request, response);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectPreflightWhenHeaderIsNotConfigured() throws IOException {
        MockHttpServletRequest request = preflight("GET", "x-unknown-header");
        MockHttpServletResponse response = new MockHttpServletResponse();

        process(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldRejectPreflightWhenMethodIsNotConfigured() throws IOException {
        MockHttpServletRequest request = preflight("TRACE", "authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        process(request, response);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private void process(MockHttpServletRequest request, MockHttpServletResponse response) throws IOException {
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        new DefaultCorsProcessor().processRequest(configuration, request, response);
    }

    private static MockHttpServletRequest preflight(String method, String headers) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/account/list");
        request.addHeader("Origin", "https://app.example.com");
        request.addHeader("Access-Control-Request-Method", method);
        request.addHeader("Access-Control-Request-Headers", headers);
        return request;
    }
}

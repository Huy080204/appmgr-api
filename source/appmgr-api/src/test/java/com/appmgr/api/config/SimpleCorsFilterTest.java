package com.appmgr.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SimpleCorsFilterTest {

    private final SimpleCorsFilter filter = new SimpleCorsFilter();

    @Test
    void shouldReturnOkWithoutCallingChainWhenRequestIsPreflight() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/version/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, never()).doFilter(any(), any());
        assertCorsHeaders(response);
    }

    @Test
    void shouldCallChainAndAddCorsHeadersWhenRequestIsGet() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/version/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertCorsHeaders(response);
    }

    private static void assertCorsHeaders(MockHttpServletResponse response) {
        assertThat(response.getHeader("Access-Control-Allow-Headers").toLowerCase())
                .contains("x-app-id", "x-category-id", "expo-channel-name", "expo-platform",
                        "expo-runtime-version", "expo-current-update-id");
        assertThat(response.getHeader("Access-Control-Expose-Headers").toLowerCase())
                .contains("expo-protocol-version", "expo-sfv-version");
    }
}

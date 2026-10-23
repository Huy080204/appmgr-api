package com.appmgr.api.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JsonToUrlEncodedAuthenticationFilterTest {
    private final JsonToUrlEncodedAuthenticationFilter filter = new JsonToUrlEncodedAuthenticationFilter();

    @Test
    void shouldExposeJsonBodyAsRequestParametersWhenTokenRequestIsJson() throws ServletException, IOException {
        MockHttpServletRequest request = tokenRequest("application/json",
                "{\"grant_type\":\"password\",\"username\":\"admin\",\"password\":\"secret\",\"ignored\":null,\"count\":3}");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        ServletRequest forwarded = chain.getRequest();
        assertThat(forwarded.getParameter("grant_type")).isEqualTo("password");
        assertThat(forwarded.getParameter("username")).isEqualTo("admin");
        assertThat(forwarded.getParameter("count")).isEqualTo("3");
        assertThat(forwarded.getParameterValues("password")).containsExactly("secret");
        assertThat(Collections.list(forwarded.getParameterNames())).containsExactlyInAnyOrder("grant_type", "username", "password", "count");
        assertThat(forwarded.getParameterMap()).doesNotContainKey("ignored");
    }

    @Test
    void shouldReturnNullForMissingParameterWhenTokenRequestIsJson() throws ServletException, IOException {
        MockHttpServletRequest request = tokenRequest("application/json;charset=UTF-8", "{\"grant_type\":\"password\"}");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest().getParameter("username")).isNull();
    }

    @Test
    void shouldLeaveRequestUntouchedWhenTokenRequestIsFormEncoded() throws ServletException, IOException {
        MockHttpServletRequest request = tokenRequest("application/x-www-form-urlencoded", "");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldLeaveRequestUntouchedWhenJsonRequestIsNotForTheTokenEndpoint() throws ServletException, IOException {
        MockHttpServletRequest request = tokenRequest("application/json", "{\"a\":\"b\"}");
        request.setServletPath("/v1/account/login");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void shouldLeaveRequestUntouchedWhenRequestHasNoContentType() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", SecurityConstant.TOKEN_ENDPOINT);
        request.setServletPath(SecurityConstant.TOKEN_ENDPOINT);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest tokenRequest(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", SecurityConstant.TOKEN_ENDPOINT);
        request.setServletPath(SecurityConstant.TOKEN_ENDPOINT);
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}

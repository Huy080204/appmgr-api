package com.appmgr.api.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
@Slf4j
public class JsonToUrlEncodedAuthenticationFilter extends OncePerRequestFilter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (Objects.equals(request.getServletPath(), SecurityConstant.TOKEN_ENDPOINT) && request.getContentType() != null && request.getContentType().contains("application/json")) {

            byte[] json = request.getInputStream().readAllBytes();

            Map<String, Object> jsonMap = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, String[]> parameters = new HashMap<>();
            jsonMap.forEach((name, value) -> {
                if (value != null) {
                    parameters.put(name, new String[]{String.valueOf(value)});
                }
            });
            HttpServletRequest requestWrapper = new RequestWrapper(request, parameters);
            filterChain.doFilter(requestWrapper, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static class RequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> params;

        RequestWrapper(HttpServletRequest request, Map<String, String[]> params) {
            super(request);
            this.params = params;
        }

        @Override
        public String getParameter(String name) {
            if (this.params.containsKey(name)) {
                return this.params.get(name)[0];
            }
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return this.params;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(params.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return params.get(name);
        }
    }
}

package com.appmgr.api.config;

import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
@Profile({"local", "dev"})
public class SwaggerConfig {
    private static final String SWAGGER_UI_PATH = "/swagger-ui/";
    private static final String HTTPS = "https";

    @Bean
    public GroupedOpenApi appmgrApi() {
        return GroupedOpenApi.builder()
                .group("appmgr-api")
                .packagesToScan("com.appmgr.api.controller")
                .build();
    }

    /**
     * Behind the TLS-terminating proxy the app only sees http, so the generated server url is http://...
     * Swagger UI's request for the api-docs carries the page url as Referer: when that page is https on the
     * same host, upgrade the scheme. The host always comes from the generated url, never from the Referer.
     */
    @Bean
    public ServerBaseUrlCustomizer swaggerUiHttpsServerUrl() {
        return serverBaseUrl -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return serverBaseUrl;
            }
            String referer = ((ServletRequestAttributes) attributes).getRequest().getHeader(HttpHeaders.REFERER);
            if (referer == null || !referer.contains(SWAGGER_UI_PATH)) {
                return serverBaseUrl;
            }
            try {
                URI page = new URI(referer);
                URI server = new URI(serverBaseUrl);
                if (!HTTPS.equalsIgnoreCase(page.getScheme()) || server.getHost() == null
                        || !server.getHost().equalsIgnoreCase(page.getHost())) {
                    return serverBaseUrl;
                }
                return new URI(HTTPS, null, server.getHost(), page.getPort(), server.getPath(), null, null).toString();
            } catch (URISyntaxException e) {
                return serverBaseUrl;
            }
        };
    }
}

package com.appmgr.api.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MethodSecurityConfigTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowCallWhenAuthorityIsRolePrefixedForTheRequiredRole() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class, SecuredBeanConfig.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "n/a", "ROLE_ACC_C"));

            String result = context.getBean(SecuredBean.class).create();

            assertThat(result).isEqualTo("created");
        }
    }

    @Test
    void shouldDenyCallWhenAuthorityIsAnotherRole() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class, SecuredBeanConfig.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "n/a", "ROLE_ACC_V"));
            SecuredBean securedBean = context.getBean(SecuredBean.class);

            assertThatThrownBy(securedBean::create).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void shouldDenyCallWhenAuthorityHasNoRolePrefix() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class, SecuredBeanConfig.class)) {
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "n/a", "ACC_C"));
            SecuredBean securedBean = context.getBean(SecuredBean.class);

            assertThatThrownBy(securedBean::create).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Configuration
    static class SecuredBeanConfig {
        @Bean
        SecuredBean securedBean() {
            return new SecuredBean();
        }
    }

    static class SecuredBean {
        @PreAuthorize("hasRole('ACC_C')")
        public String create() {
            return "created";
        }
    }
}

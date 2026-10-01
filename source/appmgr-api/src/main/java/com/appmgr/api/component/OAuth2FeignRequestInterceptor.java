package com.appmgr.api.component;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.service.impl.UserServiceImpl;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2FeignRequestInterceptor implements RequestInterceptor { // cấu hình cho các cuộc gọi đến các service khác
    @Autowired
    private UserServiceImpl userService;

    @Override
    public void apply(RequestTemplate template) {
        if (userService.getCurrentToken() != null) {
            log.error("-----------> Constructing Header {} for Token {}, token {}", BaseConstant.AUTHORIZATION_HEADER, OAuth2AccessToken.BEARER_TYPE, String.format("%s %s", OAuth2AccessToken.BEARER_TYPE, userService.getCurrentToken()));
            template.header(BaseConstant.AUTHORIZATION_HEADER, String.format("%s %s", OAuth2AccessToken.BEARER_TYPE, userService.getCurrentToken()));
        }
    }
}

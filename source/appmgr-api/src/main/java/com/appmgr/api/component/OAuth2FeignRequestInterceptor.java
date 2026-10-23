package com.appmgr.api.component;

import com.appmgr.api.constant.BaseConstant;
import com.appmgr.api.service.impl.UserServiceImpl;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class OAuth2FeignRequestInterceptor implements RequestInterceptor { // cấu hình cho các cuộc gọi đến các service khác

    @Autowired
    private UserServiceImpl userService;

    @Override
    public void apply(RequestTemplate template) {
        if (userService.getCurrentToken() != null) {
            log.error("-----------> Constructing Header {} for Token {}", BaseConstant.AUTHORIZATION_HEADER, BaseConstant.BEARER_TYPE);
            template.header(BaseConstant.AUTHORIZATION_HEADER, BaseConstant.BEARER_TYPE + " " + userService.getCurrentToken());
        }
    }
}

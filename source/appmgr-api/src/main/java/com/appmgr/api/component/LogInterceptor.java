package com.appmgr.api.component;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class LogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);

        long startTime = (Long) request.getAttribute("startTime");
        long endTime = System.currentTimeMillis();
        long executeTime = endTime - startTime;

        try {
            MDC.put("request", getUrl(request));
            MDC.put("executeTime", executeTime + "");
            if (ex != null) {
                log.error("afterCompletion>> {}", ex.getMessage());
            } else {
                log.debug("[{}], executeTime: {}ms", getUrl(request), executeTime);
            }
        } finally {
            MDC.remove("request");
            MDC.remove("executeTime");
        }
    }

    private static String getUrl(HttpServletRequest req) {
        String reqUrl = req.getRequestURL().toString();
        String queryString = req.getQueryString();   // d = 789
        if (StringUtils.hasLength(queryString)) {
            reqUrl += "?" + queryString;
        }
        return reqUrl;
    }
}

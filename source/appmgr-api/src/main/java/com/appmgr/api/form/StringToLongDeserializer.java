package com.appmgr.api.form;

import com.appmgr.api.constant.BaseConstant;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Objects;

@Slf4j
public class StringToLongDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return safeParseLong(p);
        }

        HttpServletRequest request = attrs.getRequest();
        String client = request.getHeader(BaseConstant.HEADER_CLIENT_TYPE);

        if (Objects.equals(client, BaseConstant.HEADER_CLIENT_TYPE_WEB)) {
            String value = p.getText();
            if (value == null || value.isEmpty()) return null;
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException ex) {
                log.warn("Failed to parse Long from string: '{}'", value, ex);
                return null;
            }
        }
        return safeParseLong(p);
    }

    private Long safeParseLong(JsonParser p) {
        try {
            return p.getValueAsLong();
        } catch (Exception e) {
            log.warn("Failed parse long default", e);
            return null;
        }
    }
}

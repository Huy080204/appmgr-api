package com.appmgr.api.config;

import com.appmgr.api.dto.ApiMessageDto;
import com.appmgr.api.exception.BadRequestException;
import com.appmgr.api.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

@Slf4j
@Component
public class CustomFeignErrorDecoder implements ErrorDecoder {
    @Autowired
    ObjectMapper objectMapper;

    @Override
    public Exception decode(String methodKey, Response response) {
        Request request = response.request();
        String message = response.body().toString();
        switch (response.status()) {
            case 401:
                return new RetryableException(401, message, request.httpMethod(), new Date(), request);
            case 403:
                return new RetryableException(403, message, request.httpMethod(), new Date(), request);
            case 400:
                try (InputStream inputStream = response.body().asInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder responseBody = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                    ApiMessageDto<?> apiMessageDto = objectMapper.readValue(responseBody.toString(), ApiMessageDto.class);
                    return new BadRequestException(apiMessageDto.getMessage(), apiMessageDto.getCode());
                } catch (Exception e) {
                    return new BadRequestException("Unexpected exception: " + e.getMessage());
                }
            case 404:
                try (InputStream inputStream = response.body().asInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder responseBody = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                    ApiMessageDto<?> apiMessageDto = objectMapper.readValue(responseBody.toString(), ApiMessageDto.class);
                    return new NotFoundException(apiMessageDto.getMessage(), apiMessageDto.getCode());
                } catch (Exception e) {
                    return new NotFoundException(message);
                }
            default:
                return new RuntimeException(message);
        }
    }
}

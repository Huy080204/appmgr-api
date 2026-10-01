package com.appmgr.api.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BadRequestException extends RuntimeException {
    private String code;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, String code) {
        super(message);
        this.code = code;
    }
}

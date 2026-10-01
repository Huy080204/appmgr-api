package com.appmgr.api.exception;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnauthorizationException extends RuntimeException {
    private String code;

    public UnauthorizationException(String message) {
        super(message);
    }

    public UnauthorizationException(String message, String code) {
        super(message);
        this.code = code;
    }
}

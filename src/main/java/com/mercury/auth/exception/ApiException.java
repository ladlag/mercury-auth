package com.mercury.auth.exception;

import lombok.Getter;

public class ApiException extends RuntimeException {
    @Getter
    private final String code;

    public ApiException(String code, String message) {
        super(message);
        this.code = code;
    }
}

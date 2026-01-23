package com.mercury.auth.exception;

import lombok.Getter;

import java.util.Objects;

public class ApiException extends RuntimeException {
    @Getter
    private final ErrorCodes code;

    public ApiException(ErrorCodes code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String getCodeValue() {
        return code.getCode();
    }
}

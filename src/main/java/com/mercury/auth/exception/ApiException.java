package com.mercury.auth.exception;

import lombok.Getter;

public class ApiException extends RuntimeException {
    @Getter
    private final ErrorCodes code;

    public ApiException(ErrorCodes code, String message) {
        super(message);
        this.code = code;
    }

    public String getCodeValue() {
        return code == null ? null : code.getCode();
    }

    public String getCodeMessage() {
        return code == null ? null : code.getMessage();
    }
}

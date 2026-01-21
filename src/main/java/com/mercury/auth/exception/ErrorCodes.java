package com.mercury.auth.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCodes {

    // ========== 40 01 认证（登录/凭证）==========
    BAD_CREDENTIALS      ("400101", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    USER_DISABLED        ("400102", "User is disabled", HttpStatus.FORBIDDEN),
    PASSWORD_MISMATCH    ("400103", "Password mismatch", HttpStatus.BAD_REQUEST),

    // ========== 40 02 Token ==========
    INVALID_TOKEN        ("400201", "Invalid or expired token", HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED    ("400202", "Token has been revoked", HttpStatus.UNAUTHORIZED),

    // ========== 40 03 用户 ==========
    USER_NOT_FOUND       ("400301", "User not found", HttpStatus.NOT_FOUND),
    DUPLICATE_USERNAME   ("400302", "Username already exists", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL      ("400303", "Email already exists", HttpStatus.CONFLICT),
    DUPLICATE_PHONE      ("400304", "Phone already exists", HttpStatus.CONFLICT),

    // ========== 40 04 租户 ==========
    TENANT_NOT_FOUND     ("400401", "Tenant not found", HttpStatus.NOT_FOUND),
    TENANT_DISABLED      ("400402", "Tenant is disabled", HttpStatus.FORBIDDEN),
    TENANT_MISMATCH      ("400403", "Tenant mismatch", HttpStatus.FORBIDDEN),
    DUPLICATE_TENANT     ("400404", "Tenant already exists", HttpStatus.CONFLICT),

    // ========== 40 06 验证码/人机校验 ==========
    INVALID_CODE         ("400601", "Invalid verification code", HttpStatus.BAD_REQUEST),
    CAPTCHA_REQUIRED     ("400602", "Captcha required", HttpStatus.PRECONDITION_REQUIRED),

    // ========== 40 07 限流/风控 ==========
    RATE_LIMITED         ("400701", "Too many requests", HttpStatus.TOO_MANY_REQUESTS),

    // ========== 40 08 参数校验 ==========
    VALIDATION_FAILED    ("400801", "Validation failed", HttpStatus.BAD_REQUEST),

    // ========== 40 99 系统 ==========
    INTERNAL_ERROR       ("409901", "Internal error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCodes(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
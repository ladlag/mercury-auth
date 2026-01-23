package com.mercury.auth.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCodes {

    // ========== 40 01 认证（登录/凭证）==========
    BAD_CREDENTIALS      ("400101", "error.400101", HttpStatus.UNAUTHORIZED),
    USER_DISABLED        ("400102", "error.400102", HttpStatus.FORBIDDEN),
    PASSWORD_MISMATCH    ("400103", "error.400103", HttpStatus.BAD_REQUEST),

    // ========== 40 02 Token ==========
    INVALID_TOKEN        ("400201", "error.400201", HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED    ("400202", "error.400202", HttpStatus.UNAUTHORIZED),

    // ========== 40 03 用户 ==========
    USER_NOT_FOUND       ("400301", "error.400301", HttpStatus.NOT_FOUND),
    DUPLICATE_USERNAME   ("400302", "error.400302", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL      ("400303", "error.400303", HttpStatus.CONFLICT),
    DUPLICATE_PHONE      ("400304", "error.400304", HttpStatus.CONFLICT),

    // ========== 40 04 租户 ==========
    TENANT_NOT_FOUND     ("400401", "error.400401", HttpStatus.NOT_FOUND),
    TENANT_DISABLED      ("400402", "error.400402", HttpStatus.FORBIDDEN),
    TENANT_MISMATCH      ("400403", "error.400403", HttpStatus.FORBIDDEN),
    DUPLICATE_TENANT     ("400404", "error.400404", HttpStatus.CONFLICT),
    MISSING_TENANT_HEADER("400405", "error.400405", HttpStatus.BAD_REQUEST),

    // ========== 40 06 验证码/人机校验 ==========
    INVALID_CODE         ("400601", "error.400601", HttpStatus.BAD_REQUEST),
    CAPTCHA_REQUIRED     ("400602", "error.400602", HttpStatus.PRECONDITION_REQUIRED),
    CAPTCHA_INVALID      ("400603", "error.400603", HttpStatus.BAD_REQUEST),

    // ========== 40 07 限流/风控 ==========
    RATE_LIMITED         ("400701", "error.400701", HttpStatus.TOO_MANY_REQUESTS),

    // ========== 40 08 参数校验 ==========
    VALIDATION_FAILED    ("400801", "error.400801", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY ("400802", "error.400802", HttpStatus.BAD_REQUEST),

    // ========== 40 09 服务配置 ==========
    EMAIL_SERVICE_UNAVAILABLE ("400901", "error.400901", HttpStatus.SERVICE_UNAVAILABLE),

    // ========== 40 99 系统 ==========
    INTERNAL_ERROR       ("409901", "error.409901", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String messageKey;
    private final HttpStatus httpStatus;

    ErrorCodes(String code, String messageKey, HttpStatus httpStatus) {
        this.code = code;
        this.messageKey = messageKey;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

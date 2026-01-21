package com.mercury.auth.exception;

public interface ErrorCodes {
    String USER_NOT_FOUND = "USER_NOT_FOUND";
    String TENANT_NOT_FOUND = "TENANT_NOT_FOUND";
    String USER_DISABLED = "USER_DISABLED";
    String BAD_CREDENTIALS = "BAD_CREDENTIALS";
    String DUPLICATE_USERNAME = "DUPLICATE_USERNAME";
    String DUPLICATE_TENANT = "DUPLICATE_TENANT";
    String DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    String DUPLICATE_PHONE = "DUPLICATE_PHONE";
    String PASSWORD_MISMATCH = "PASSWORD_MISMATCH";
    String INVALID_CODE = "INVALID_CODE";
    String CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED";
    String RATE_LIMITED = "RATE_LIMITED";
    String INVALID_TOKEN = "INVALID_TOKEN";
    String TOKEN_BLACKLISTED = "TOKEN_BLACKLISTED";
    String TENANT_MISMATCH = "TENANT_MISMATCH";
    String TENANT_DISABLED = "TENANT_DISABLED";
    String VALIDATION_FAILED = "VALIDATION_FAILED";
    String INTERNAL_ERROR = "INTERNAL_ERROR";

    static String toNumeric(String code) {
        if (code == null) {
            return "999";
        }
        switch (code) {
            case USER_NOT_FOUND:
                return "101";
            case TENANT_NOT_FOUND:
                return "102";
            case USER_DISABLED:
                return "103";
            case BAD_CREDENTIALS:
                return "104";
            case DUPLICATE_USERNAME:
                return "105";
            case DUPLICATE_TENANT:
                return "106";
            case DUPLICATE_EMAIL:
                return "107";
            case DUPLICATE_PHONE:
                return "108";
            case PASSWORD_MISMATCH:
                return "109";
            case INVALID_CODE:
                return "110";
            case CAPTCHA_REQUIRED:
                return "111";
            case RATE_LIMITED:
                return "112";
            case INVALID_TOKEN:
                return "113";
            case TOKEN_BLACKLISTED:
                return "114";
            case TENANT_MISMATCH:
                return "115";
            case TENANT_DISABLED:
                return "116";
            case VALIDATION_FAILED:
                return "117";
            case INTERNAL_ERROR:
                return "118";
            default:
                return "999";
        }
    }
}

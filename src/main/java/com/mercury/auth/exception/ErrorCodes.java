package com.mercury.auth.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    Map<String, String> NUMERIC_CODES = buildNumericCodes();

    static Map<String, String> buildNumericCodes() {
        Map<String, String> codes = new HashMap<>();
        codes.put(USER_NOT_FOUND, "101");
        codes.put(TENANT_NOT_FOUND, "102");
        codes.put(USER_DISABLED, "103");
        codes.put(BAD_CREDENTIALS, "104");
        codes.put(DUPLICATE_USERNAME, "105");
        codes.put(DUPLICATE_TENANT, "106");
        codes.put(DUPLICATE_EMAIL, "107");
        codes.put(DUPLICATE_PHONE, "108");
        codes.put(PASSWORD_MISMATCH, "109");
        codes.put(INVALID_CODE, "110");
        codes.put(CAPTCHA_REQUIRED, "111");
        codes.put(RATE_LIMITED, "112");
        codes.put(INVALID_TOKEN, "113");
        codes.put(TOKEN_BLACKLISTED, "114");
        codes.put(TENANT_MISMATCH, "115");
        codes.put(TENANT_DISABLED, "116");
        codes.put(VALIDATION_FAILED, "117");
        codes.put(INTERNAL_ERROR, "118");
        return Collections.unmodifiableMap(codes);
    }

    static String toNumeric(String code) {
        if (code == null) {
            return "999";
        }
        return NUMERIC_CODES.getOrDefault(code, "999");
    }
}

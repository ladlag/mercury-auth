package com.mercury.auth.util;

import org.springframework.web.util.HtmlUtils;

public final class SanitizationUtils {

    private SanitizationUtils() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(value);
    }
}

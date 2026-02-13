package com.mercury.auth.util;

import org.springframework.web.util.HtmlUtils;

/**
 * Utility class for sanitizing user input to prevent XSS attacks.
 * 
 * Delegates to Spring's {@link HtmlUtils#htmlEscape(String)} which is a
 * well-tested, comprehensive HTML escaping utility that handles all HTML
 * special characters including &lt;, &gt;, &amp;, ", and '.
 * 
 * Although this application is a pure REST JSON API (no HTML rendering),
 * sanitizing output data provides defense-in-depth against potential XSS
 * if the data is later consumed by a web frontend that renders it unsafely.
 */
public final class XssSanitizer {

    private XssSanitizer() {
        // Utility class, prevent instantiation
    }

    /**
     * Sanitize a string by escaping HTML special characters using Spring's HtmlUtils.
     *
     * @param input the string to sanitize
     * @return the sanitized string, or null if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(input);
    }
}

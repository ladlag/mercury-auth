package com.mercury.auth.util;

/**
 * Utility class for sanitizing user input to prevent XSS attacks.
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
     * Sanitize a string by escaping HTML special characters.
     * Replaces &lt;, &gt;, &amp;, ", and ' with their HTML entity equivalents.
     *
     * @param input the string to sanitize
     * @return the sanitized string, or null if input is null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#x27;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
}

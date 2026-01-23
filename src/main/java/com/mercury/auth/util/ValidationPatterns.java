package com.mercury.auth.util;

/**
 * Constants for validation patterns
 */
public final class ValidationPatterns {
    
    private ValidationPatterns() {
        // Utility class, prevent instantiation
    }
    
    /**
     * Chinese mobile phone number pattern
     * Format: 11 digits, starts with 1, second digit is 3-9
     * Examples: 13812345678, 15912345678, 18812345678
     */
    public static final String CHINESE_MOBILE_PHONE = "^1[3-9]\\d{9}$";
}

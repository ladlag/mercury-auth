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
    
    /**
     * Username pattern
     * Format: 6-20 characters, letters or letters and numbers only, must start with letter
     * Examples: user123, johnsmith, adminuser
     */
    public static final String USERNAME = "^[a-zA-Z][a-zA-Z0-9]{5,19}$";
    
    /**
     * Password pattern
     * Format: Can contain letters, numbers, and special characters (no whitespace allowed)
     * Length: 6-20 characters (enforced separately via @Size annotation)
     * Note: Length validation is done via @Size(min=6, max=20) annotation
     */
    public static final String PASSWORD = "^[a-zA-Z0-9\\p{Punct}]+$";
}

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
     * Tenant ID pattern
     * Format: 1-50 characters, letters, numbers, underscores, or hyphens
     * Matches X-Tenant-Id header validation (TenantIdHeaderInjector) to keep identifiers URL-safe and consistent
     * Examples: tenant1, tenant-001, tenant_abc
     */
    public static final String TENANT_ID = "^[a-zA-Z0-9_-]{1,50}$";

    /**
     * Tenant name pattern
     * Format: 1-50 characters, Unicode letters/numbers/spaces/underscore/hyphen only
     * Must start and end with a letter/number to avoid leading/trailing spaces (single-character names allowed)
     * Examples: Mercury Auth, tenant_001, 租户一号
     */
    public static final String TENANT_NAME = "^[\\p{L}\\p{N}](?:[\\p{L}\\p{N} _-]{0,48}[\\p{L}\\p{N}])?$";
    
    /**
     * Password pattern
     * Format: Can contain letters, numbers, and special characters (no whitespace allowed)
     * Length: 6-20 characters (enforced separately via @Size annotation)
     * Note: Length validation is done via @Size(min=6, max=20) annotation
     */
    public static final String PASSWORD = "^[a-zA-Z0-9\\p{Punct}]+$";
}

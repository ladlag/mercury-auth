package com.mercury.auth.util;

import com.mercury.auth.dto.AuthAction;

/**
 * Utility class for building consistent Redis/cache keys across services
 */
public final class KeyUtils {

    private KeyUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Build a rate limit key for tracking request rates
     *
     * @param action     The auth action being rate limited
     * @param tenantId   Tenant identifier
     * @param identifier User identifier (username, email, phone, etc.)
     * @return Rate limit cache key
     */
    public static String buildRateLimitKey(AuthAction action, String tenantId, String identifier) {
        return "rate:" + action.name() + ":" + tenantId + ":" + identifier;
    }

    /**
     * Build a captcha failure tracking key
     *
     * @param action     The auth action requiring captcha
     * @param tenantId   Tenant identifier
     * @param identifier User identifier (username, email, phone, etc.)
     * @return Captcha failure tracking cache key
     */
    public static String buildCaptchaKey(AuthAction action, String tenantId, String identifier) {
        String safeIdentifier = identifier == null ? "unknown" : identifier;
        return "captcha:fail:" + action.name() + ":" + tenantId + ":" + safeIdentifier;
    }

    /**
     * Build a password reset code key
     *
     * @param tenantId Tenant identifier
     * @param email    Email address
     * @return Password reset code cache key
     */
    public static String passwordResetCodeKey(String tenantId, String email) {
        return "code:password-reset:" + tenantId + ":" + email;
    }

    /**
     * Build an email verification code key
     *
     * @param tenantId Tenant identifier
     * @param email    Email address
     * @return Email verification code cache key
     */
    public static String emailVerificationKey(String tenantId, String email) {
        return "code:email-verify:" + tenantId + ":" + email;
    }
}

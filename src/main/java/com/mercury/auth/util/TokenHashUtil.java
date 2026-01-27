package com.mercury.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Utility class for token hashing operations.
 * Provides consistent SHA-256 hashing for tokens across the application.
 */
public class TokenHashUtil {

    private TokenHashUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Hash a token using SHA-256.
     * This creates a consistent hash that can be used as a cache key or for storage.
     *
     * @param token The JWT token to hash
     * @return Base64-encoded SHA-256 hash of the token
     */
    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}

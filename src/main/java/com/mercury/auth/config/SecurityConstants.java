package com.mercury.auth.config;

/**
 * Constants for security configuration to maintain consistency
 * between SecurityConfig and JwtAuthenticationFilter
 */
public final class SecurityConstants {
    
    private SecurityConstants() {
        // Utility class - prevent instantiation
    }
    
    // Public endpoint patterns that don't require JWT authentication
    public static final String[] PUBLIC_ENDPOINTS = {
        "/api/auth/login-**",
        "/api/auth/register-**",
        "/api/auth/send-**",
        "/api/auth/verify-**",
        "/api/auth/wechat-**",
        "/api/auth/refresh-token",
        "/api/auth/verify-token",
        "/api/auth/captcha",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/**"
    };
    
    /**
     * Check if a given path matches any of the public endpoint patterns
     */
    public static boolean isPublicEndpoint(String path) {
        for (String pattern : PUBLIC_ENDPOINTS) {
            // Convert Ant-style pattern to simple startsWith check
            String prefix = pattern.replace("**", "");
            if (path.startsWith(prefix)) {
                return true;
            }
            // Exact match for non-wildcard patterns
            if (!pattern.contains("*") && path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}

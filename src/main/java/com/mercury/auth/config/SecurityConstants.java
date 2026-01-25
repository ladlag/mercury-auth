package com.mercury.auth.config;

import java.util.regex.Pattern;

/**
 * Constants for security configuration to maintain consistency
 * between SecurityConfig and JwtAuthenticationFilter
 */
public final class SecurityConstants {
    
    private SecurityConstants() {
        // Utility class - prevent instantiation
    }
    
    // Public endpoint patterns for Spring Security antMatchers (Ant-style patterns)
    // Note: These are used by Spring Security's antMatchers() which uses path pattern matching
    public static final String[] PUBLIC_ENDPOINTS = {
        "/api/auth/login-*",
        "/api/auth/register-*",
        "/api/auth/send-*",
        "/api/auth/verify-*",
        "/api/auth/wechat-*",
        "/api/auth/quick-login-*",
        "/api/auth/refresh-token",
        "/api/auth/verify-token",
        "/api/auth/captcha",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/public-key",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/health/**"
    };
    
    // Precise regex patterns for runtime validation (used in JwtAuthenticationFilter)
    // These provide more precise matching than Ant patterns to prevent security issues
    private static final Pattern[] PUBLIC_ENDPOINT_PATTERNS = {
        Pattern.compile("^/api/auth/login-[^/]+$"),      // /api/auth/login-password, /api/auth/login-email, etc.
        Pattern.compile("^/api/auth/register-[^/]+$"),   // /api/auth/register-password, /api/auth/register-email, etc.
        Pattern.compile("^/api/auth/send-[^/]+$"),       // /api/auth/send-email-code, /api/auth/send-phone-code, etc.
        Pattern.compile("^/api/auth/verify-[^/]+$"),     // /api/auth/verify-email-code, /api/auth/verify-phone-code, etc.
        Pattern.compile("^/api/auth/wechat-[^/]+$"),     // /api/auth/wechat-login, etc.
        Pattern.compile("^/api/auth/quick-login-[^/]+$"), // /api/auth/quick-login-phone, /api/auth/quick-login-email, etc.
    };
    
    // Exact match public endpoints (no wildcards)
    private static final String[] EXACT_PUBLIC_ENDPOINTS = {
        "/api/auth/refresh-token",
        "/api/auth/verify-token",
        "/api/auth/captcha",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/public-key",
    };
    
    // Documentation and health check endpoints
    private static final Pattern[] DOCUMENTATION_PATTERNS = {
        Pattern.compile("^/v3/api-docs.*"),
        Pattern.compile("^/swagger-ui.*"),
        Pattern.compile("^/actuator/health.*"),
    };
    
    /**
     * Check if a given path matches any of the public endpoint patterns
     * Uses regex patterns for precise matching to prevent security issues
     * 
     * This method is used by JwtAuthenticationFilter for runtime validation
     * 
     * @param path the request path to check
     * @return true if the path is a public endpoint, false otherwise
     */
    public static boolean isPublicEndpoint(String path) {
        // Check exact matches first (most efficient)
        for (String exactPath : EXACT_PUBLIC_ENDPOINTS) {
            if (path.equals(exactPath)) {
                return true;
            }
        }
        
        // Check public endpoint patterns
        for (Pattern pattern : PUBLIC_ENDPOINT_PATTERNS) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        
        // Check documentation patterns
        for (Pattern pattern : DOCUMENTATION_PATTERNS) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        
        return false;
    }
}

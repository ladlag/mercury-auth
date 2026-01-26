package com.mercury.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for operation-specific rate limiting.
 * Allows different rate limits for different types of operations.
 */
@Configuration
@ConfigurationProperties(prefix = "security.rate-limit")
@Data
public class RateLimitConfig {
    
    /**
     * Default rate limit settings (backward compatibility)
     */
    private long maxAttempts = 10;
    private long windowMinutes = 1;
    
    /**
     * IP-based rate limiting settings
     */
    private IpRateLimit ip = new IpRateLimit();
    
    /**
     * Verification code sending rate limits (more restrictive)
     */
    private OperationRateLimit sendCode = new OperationRateLimit(5, 1);
    
    /**
     * Login operation rate limits
     */
    private OperationRateLimit login = new OperationRateLimit(10, 1);
    
    /**
     * Captcha generation rate limits (more permissive since users need multiple attempts)
     */
    private OperationRateLimit captcha = new OperationRateLimit(20, 1);
    
    /**
     * Token refresh rate limits
     */
    private OperationRateLimit refreshToken = new OperationRateLimit(10, 1);
    
    @Data
    public static class IpRateLimit {
        private long maxAttempts = 50;
        private long windowMinutes = 1;
    }
    
    @Data
    public static class OperationRateLimit {
        private long maxAttempts;
        private long windowMinutes;
        
        public OperationRateLimit() {
        }
        
        public OperationRateLimit(long maxAttempts, long windowMinutes) {
            this.maxAttempts = maxAttempts;
            this.windowMinutes = windowMinutes;
        }
    }
}

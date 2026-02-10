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
     * Auto-blacklist settings for failed login attempts
     */
    private AutoBlacklist autoBlacklist = new AutoBlacklist();
    
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
    
    /**
     * Daily registration limits per tenant/IP
     * This limits how many users can register from same IP per tenant per day
     */
    private DailyRegistrationLimit dailyRegistration = new DailyRegistrationLimit();
    
    @Data
    public static class IpRateLimit {
        private long maxAttempts = 50;
        private long windowMinutes = 1;
    }
    
    /**
     * Configuration for automatic IP blacklisting based on failed attempts.
     * This provides a coordinated approach with rate limiting:
     * - Rate limit: Short-term protection (temporary blocking)
     * - Auto-blacklist: Longer-term protection after repeated violations
     */
    @Data
    public static class AutoBlacklist {
        /**
         * Enable auto-blacklist feature
         */
        private boolean enabled = true;
        
        /**
         * Number of failed attempts within window to trigger auto-blacklist
         */
        private long failureThreshold = 20;
        
        /**
         * Time window in minutes to count failures
         */
        private long failureWindowMinutes = 5;
        
        /**
         * Duration in minutes to blacklist the IP
         */
        private long blacklistDurationMinutes = 30;
        
        /**
         * Severe violation threshold (triggers longer blacklist)
         */
        private long severeFailureThreshold = 50;
        
        /**
         * Time window in minutes for severe violation detection
         */
        private long severeFailureWindowMinutes = 10;
        
        /**
         * Duration in minutes for severe violation blacklist
         */
        private long severeBlacklistDurationMinutes = 120;
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
    
    /**
     * Daily registration limit configuration
     * Limits number of registrations per tenant/per IP per day
     */
    @Data
    public static class DailyRegistrationLimit {
        /**
         * Maximum number of registrations allowed per tenant per IP per day
         */
        private long maxRegistrationsPerDay = 10;
        
        /**
         * Whether to enable this limit
         */
        private boolean enabled = true;
    }
}

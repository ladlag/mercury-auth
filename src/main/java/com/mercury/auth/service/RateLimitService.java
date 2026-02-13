package com.mercury.auth.service;

import com.mercury.auth.config.RateLimitConfig;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    private final BlacklistService blacklistService;
    
    // Lua script for atomic increment with expiration
    private static final String RATE_LIMIT_SCRIPT = 
        "local current = redis.call('incr', KEYS[1]) " +
        "if current == 1 then " +
        "    redis.call('expire', KEYS[1], ARGV[1]) " +
        "end " +
        "return current";
    private static final RedisScript<Long> RATE_LIMIT_REDIS_SCRIPT =
        RedisScript.of(RATE_LIMIT_SCRIPT, Long.class);

    /**
     * Check rate limit for a specific key (tenant + identifier based)
     * Uses Lua script for atomic increment + expiration to prevent race conditions
     * 
     * @param key Redis key for rate limiting
     * @param action Auth action to determine which rate limit configuration to use
     */
    public void check(String key, AuthAction action) {
        RateLimitConfig.OperationRateLimit limit = getRateLimitForAction(action);
        
        Long count = redisTemplate.execute(
            RATE_LIMIT_REDIS_SCRIPT,
            Collections.singletonList(key),
            String.valueOf(limit.getWindowMinutes() * 60) // Convert to seconds
        );
        if (count != null && count > limit.getMaxAttempts()) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests");
        }
    }
    
    /**
     * Legacy method for backward compatibility
     * Uses default rate limit configuration
     */
    public void check(String key) {
        Long count = redisTemplate.execute(
            RATE_LIMIT_REDIS_SCRIPT,
            Collections.singletonList(key),
            String.valueOf(rateLimitConfig.getWindowMinutes() * 60) // Convert to seconds
        );
        if (count != null && count > rateLimitConfig.getMaxAttempts()) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests");
        }
    }
    
    /**
     * Record a failed login attempt and check if auto-blacklist should be triggered.
     * This provides coordinated protection:
     * 1. Rate limiting: Short-term protection (temporary blocking within time window)
     * 2. Failure tracking: Counts failures over longer window
     * 3. Auto-blacklist: Triggered when failure threshold exceeded
     * 
     * @param tenantId Tenant ID for tenant-specific blacklist
     * @param identifier User identifier (username, email, etc.)
     */
    public void recordFailedLoginAttempt(String tenantId, String identifier) {
        if (!rateLimitConfig.getAutoBlacklist().isEnabled()) {
            return;
        }
        
        try {
            String clientIp = getCurrentRequestIp();
            if (clientIp == null) {
                return; // Cannot track without IP
            }
            
            // Track failures in two time windows for different thresholds
            String failureKey = "login:failures:" + clientIp;
            String severeFailureKey = "login:failures:severe:" + clientIp;
            
            // Increment both counters
            Long failureCount = redisTemplate.execute(
                RATE_LIMIT_REDIS_SCRIPT,
                Collections.singletonList(failureKey),
                String.valueOf(rateLimitConfig.getAutoBlacklist().getFailureWindowMinutes() * 60)
            );
            
            Long severeCount = redisTemplate.execute(
                RATE_LIMIT_REDIS_SCRIPT,
                Collections.singletonList(severeFailureKey),
                String.valueOf(rateLimitConfig.getAutoBlacklist().getSevereFailureWindowMinutes() * 60)
            );
            
            // Check severe threshold first (longer blacklist)
            if (severeCount != null && severeCount >= rateLimitConfig.getAutoBlacklist().getSevereFailureThreshold()) {
                long durationMinutes = rateLimitConfig.getAutoBlacklist().getSevereBlacklistDurationMinutes();
                // Validate duration fits in int range (blacklist method expects int)
                int duration = durationMinutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) durationMinutes;
                
                blacklistService.autoBlacklistIp(
                    clientIp,
                    tenantId,
                    String.format("%d failed login attempts in %d minutes", 
                        severeCount, 
                        rateLimitConfig.getAutoBlacklist().getSevereFailureWindowMinutes()),
                    duration
                );
                logger.warn("Severe violation detected - IP auto-blacklisted: ip={} tenant={} failures={} duration={}min",
                    clientIp, tenantId, severeCount, duration);
                
                // Clear counters after blacklisting
                redisTemplate.delete(failureKey);
                redisTemplate.delete(severeFailureKey);
            }
            // Check normal threshold (shorter blacklist)
            else if (failureCount != null && failureCount >= rateLimitConfig.getAutoBlacklist().getFailureThreshold()) {
                long durationMinutes = rateLimitConfig.getAutoBlacklist().getBlacklistDurationMinutes();
                // Validate duration fits in int range (blacklist method expects int)
                int duration = durationMinutes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) durationMinutes;
                
                blacklistService.autoBlacklistIp(
                    clientIp,
                    tenantId,
                    String.format("%d failed login attempts in %d minutes", 
                        failureCount, 
                        rateLimitConfig.getAutoBlacklist().getFailureWindowMinutes()),
                    duration
                );
                logger.warn("Auto-blacklist triggered: ip={} tenant={} failures={} duration={}min",
                    clientIp, tenantId, failureCount, duration);
                
                // Clear counter after blacklisting
                redisTemplate.delete(failureKey);
            }
            
        } catch (Exception e) {
            // Don't fail the request if auto-blacklist tracking fails
            logger.error("Failed to record login failure for auto-blacklist: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get rate limit configuration for a specific action
     */
    private RateLimitConfig.OperationRateLimit getRateLimitForAction(AuthAction action) {
        switch (action) {
            // Verification code sending - more restrictive
            case RATE_LIMIT_SEND_EMAIL_CODE:
            case RATE_LIMIT_SEND_PHONE_CODE:
                return rateLimitConfig.getSendCode();
            
            // Login operations
            case RATE_LIMIT_LOGIN_PASSWORD:
            case RATE_LIMIT_LOGIN_EMAIL:
            case RATE_LIMIT_LOGIN_PHONE:
            case RATE_LIMIT_QUICK_LOGIN_PHONE:
                return rateLimitConfig.getLogin();
            
            // Captcha generation - more permissive
            case CAPTCHA_LOGIN_PASSWORD:
            case CAPTCHA_LOGIN_EMAIL:
            case CAPTCHA_LOGIN_PHONE:
            case CAPTCHA_QUICK_LOGIN_PHONE:
                return rateLimitConfig.getCaptcha();
            
            // Token refresh
            case RATE_LIMIT_REFRESH_TOKEN:
                return rateLimitConfig.getRefreshToken();
            
            // Default rate limit for other actions
            default:
                return new RateLimitConfig.OperationRateLimit(
                    rateLimitConfig.getMaxAttempts(),
                    rateLimitConfig.getWindowMinutes()
                );
        }
    }
    
    /**
     * Check IP-based rate limit for public endpoints
     * This provides an additional layer of protection against distributed attacks
     * 
     * SECURITY: If IP extraction fails, the request is rejected to prevent
     * attackers from bypassing rate limits by triggering IP extraction failures.
     */
    public void checkIpRateLimit(String action) {
        String clientIp = requireCurrentRequestIp(ErrorCodes.RATE_LIMITED, 
            "unable to verify request source", "IP rate limiting", action);
        
        String ipKey = "rate:ip:" + action + ":" + clientIp;
        
        Long count = redisTemplate.execute(
            RATE_LIMIT_REDIS_SCRIPT,
            Collections.singletonList(ipKey),
            String.valueOf(rateLimitConfig.getIp().getWindowMinutes() * 60) // Convert to seconds
        );
        if (count != null && count > rateLimitConfig.getIp().getMaxAttempts()) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests from your IP");
        }
    }
    
    /**
     * Get current request IP address, throwing ApiException if IP cannot be determined.
     * This enforces a fail-closed security approach: if we cannot identify the client,
     * the request is rejected rather than silently bypassing protection.
     *
     * @param errorCode Error code to use in the exception
     * @param errorMessage Error message to use in the exception
     * @param context Description of what operation needs the IP (for logging)
     * @param action The action being rate-limited (for logging)
     * @return The client IP address (never null)
     * @throws ApiException if IP cannot be determined
     */
    private String requireCurrentRequestIp(ErrorCodes errorCode, String errorMessage, 
                                           String context, String action) {
        try {
            String clientIp = IpUtils.getClientIpOrNull();
            if (clientIp == null) {
                logger.warn("IP extraction failed for {} action={}, rejecting request", context, action);
                throw new ApiException(errorCode, errorMessage);
            }
            return clientIp;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during {} action={}, rejecting request: {}", 
                       context, action, e.getMessage(), e);
            throw new ApiException(errorCode, errorMessage);
        }
    }
    
    /**
     * Get current request IP address
     */
    private String getCurrentRequestIp() {
        return IpUtils.getClientIpOrNull();
    }
    
    /**
     * Check daily registration limit per tenant per IP
     * This prevents abuse by limiting how many accounts can be registered from the same IP per tenant per day
     * 
     * @param tenantId The tenant ID
     * @throws ApiException if daily registration limit is reached
     */
    public void checkDailyRegistrationLimit(String tenantId) {
        if (!rateLimitConfig.getDailyRegistration().isEnabled()) {
            return;
        }
        
        String clientIp = requireCurrentRequestIp(ErrorCodes.DAILY_REGISTRATION_LIMIT_REACHED, 
            "unable to verify request source", "daily registration limit check", "tenantId=" + tenantId);
        
        // Key format: registration:daily:<tenantId>:<ip>
        String dailyRegKey = "registration:daily:" + tenantId + ":" + clientIp;
        
        // Use 24 hours (86400 seconds) as the window
        Long count = redisTemplate.execute(
            RATE_LIMIT_REDIS_SCRIPT,
            Collections.singletonList(dailyRegKey),
            String.valueOf(86400) // 24 hours in seconds
        );
        
        if (count != null && count > rateLimitConfig.getDailyRegistration().getMaxRegistrationsPerDay()) {
            logger.warn("Daily registration limit exceeded: tenant={} ip={} count={} max={}", 
                tenantId, clientIp, count, rateLimitConfig.getDailyRegistration().getMaxRegistrationsPerDay());
            throw new ApiException(ErrorCodes.DAILY_REGISTRATION_LIMIT_REACHED, 
                "daily registration limit reached");
        }
    }
}

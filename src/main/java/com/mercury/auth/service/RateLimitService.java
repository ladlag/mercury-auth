package com.mercury.auth.service;

import com.mercury.auth.config.RateLimitConfig;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig rateLimitConfig;
    
    // Lua script for atomic increment with expiration
    private static final String RATE_LIMIT_SCRIPT = 
        "local current = redis.call('incr', KEYS[1]) " +
        "if current == 1 then " +
        "    redis.call('expire', KEYS[1], ARGV[1]) " +
        "end " +
        "return current";

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
            RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
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
            RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
            Collections.singletonList(key),
            String.valueOf(rateLimitConfig.getWindowMinutes() * 60) // Convert to seconds
        );
        if (count != null && count > rateLimitConfig.getMaxAttempts()) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests");
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
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                // No request context - fail closed for security
                throw new ApiException(ErrorCodes.RATE_LIMITED, "unable to verify request source");
            }
            
            HttpServletRequest request = attributes.getRequest();
            String clientIp = IpUtils.getClientIp(request);
            
            // If IP extraction returns "unknown", fail closed for security
            if ("unknown".equals(clientIp)) {
                org.slf4j.LoggerFactory.getLogger(RateLimitService.class)
                    .warn("IP extraction failed for rate limiting action={}, rejecting request", action);
                throw new ApiException(ErrorCodes.RATE_LIMITED, "unable to verify request source");
            }
            
            String ipKey = "rate:ip:" + action + ":" + clientIp;
            
            Long count = redisTemplate.execute(
                RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
                Collections.singletonList(ipKey),
                String.valueOf(rateLimitConfig.getIp().getWindowMinutes() * 60) // Convert to seconds
            );
            if (count != null && count > rateLimitConfig.getIp().getMaxAttempts()) {
                throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests from your IP");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Any unexpected exception during rate limiting should fail closed for security
            org.slf4j.LoggerFactory.getLogger(RateLimitService.class)
                .error("Unexpected error during IP rate limiting action={}, rejecting request: {}", 
                       action, e.getMessage(), e);
            throw new ApiException(ErrorCodes.RATE_LIMITED, "unable to verify request source");
        }
    }
}

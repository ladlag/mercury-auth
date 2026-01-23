package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    
    // Lua script for atomic increment with expiration
    private static final String RATE_LIMIT_SCRIPT = 
        "local current = redis.call('incr', KEYS[1]) " +
        "if current == 1 then " +
        "    redis.call('expire', KEYS[1], ARGV[1]) " +
        "end " +
        "return current";

    @Value("${security.rate-limit.max-attempts:10}")
    private long maxAttempts;

    @Value("${security.rate-limit.window-minutes:1}")
    private long windowMinutes;
    
    @Value("${security.rate-limit.ip.max-attempts:50}")
    private long ipMaxAttempts;
    
    @Value("${security.rate-limit.ip.window-minutes:1}")
    private long ipWindowMinutes;

    /**
     * Check rate limit for a specific key (tenant + identifier based)
     * Uses Lua script for atomic increment + expiration to prevent race conditions
     */
    public void check(String key) {
        Long count = redisTemplate.execute(
            RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
            Collections.singletonList(key),
            String.valueOf(windowMinutes * 60) // Convert to seconds
        );
        if (count != null && count > maxAttempts) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests");
        }
    }
    
    /**
     * Check IP-based rate limit for public endpoints
     * This provides an additional layer of protection against distributed attacks
     */
    public void checkIpRateLimit(String action) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String clientIp = IpUtils.getClientIp(request);
                String ipKey = "rate:ip:" + action + ":" + clientIp;
                
                Long count = redisTemplate.execute(
                    RedisScript.of(RATE_LIMIT_SCRIPT, Long.class),
                    Collections.singletonList(ipKey),
                    String.valueOf(ipWindowMinutes * 60) // Convert to seconds
                );
                if (count != null && count > ipMaxAttempts) {
                    throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests from your IP");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Log IP extraction failure but don't fail the request to maintain service availability
            // This ensures the service remains available even if there are issues with IP extraction
            org.slf4j.LoggerFactory.getLogger(RateLimitService.class)
                .debug("Failed to extract IP for rate limiting, continuing without IP-based limit: {}", e.getMessage());
        }
    }
}

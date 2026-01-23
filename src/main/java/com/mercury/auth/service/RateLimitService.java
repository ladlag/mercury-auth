package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

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
     */
    public void check(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }
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
                
                Long count = redisTemplate.opsForValue().increment(ipKey);
                if (count != null && count == 1) {
                    redisTemplate.expire(ipKey, Duration.ofMinutes(ipWindowMinutes));
                }
                if (count != null && count > ipMaxAttempts) {
                    throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests from your IP");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Don't fail requests if IP extraction fails, just log and continue
            // This ensures the service remains available even if there are issues
        }
    }
}

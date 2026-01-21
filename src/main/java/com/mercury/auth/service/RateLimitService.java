package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    @Value("${security.rate-limit.max-attempts:10}")
    private long maxAttempts;

    @Value("${security.rate-limit.window-minutes:1}")
    private long windowMinutes;

    public void check(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }
        if (count != null && count > maxAttempts) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, "too many requests");
        }
    }
}

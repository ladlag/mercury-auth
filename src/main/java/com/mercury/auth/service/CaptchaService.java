package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;

    @Value("${security.captcha.threshold:3}")
    private long threshold;

    @Value("${security.captcha.ttl-minutes:5}")
    private long ttlMinutes;

    public void recordFailure(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        }
    }

    public void reset(String key) {
        redisTemplate.delete(key);
    }

    public boolean isRequired(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= threshold;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String buildKey(AuthAction action, String tenantId, String identifier) {
        return "captcha:" + action.name() + ":" + tenantId + ":" + identifier;
    }
}

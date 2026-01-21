package com.mercury.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final StringRedisTemplate redisTemplate;
    private final Random random = new Random();

    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public void storeCode(String key, String code, Duration ttl) {
        redisTemplate.opsForValue().set(key, code, ttl);
    }

    public boolean verify(String key, String code) {
        String val = redisTemplate.opsForValue().get(key);
        return val != null && val.equals(code);
    }
}

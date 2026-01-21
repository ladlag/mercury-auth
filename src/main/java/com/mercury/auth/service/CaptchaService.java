package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.CaptchaChallenge;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

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

    public CaptchaChallenge createChallenge(AuthAction action, String tenantId, String identifier) {
        int left = random.nextInt(9) + 1;
        int right = random.nextInt(9) + 1;
        String question = left + " + " + right + " = ?";
        String answer = String.valueOf(left + right);
        String captchaId = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        redisTemplate.opsForValue().set(buildChallengeKey(action, tenantId, identifier, captchaId), answer, ttl);
        return new CaptchaChallenge(captchaId, question, ttl.getSeconds());
    }

    public boolean verifyChallenge(AuthAction action, String tenantId, String identifier, String captchaId, String answer) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(answer)) {
            return false;
        }
        String key = buildChallengeKey(action, tenantId, identifier, captchaId);
        String storedAnswer = redisTemplate.opsForValue().get(key);
        if (storedAnswer == null) {
            return false;
        }
        boolean matches = storedAnswer.trim().equalsIgnoreCase(answer.trim());
        if (matches) {
            redisTemplate.delete(key);
        }
        return matches;
    }

    private String buildChallengeKey(AuthAction action, String tenantId, String identifier, String captchaId) {
        return "captcha:challenge:" + action.name() + ":" + tenantId + ":" + identifier + ":" + captchaId;
    }
}

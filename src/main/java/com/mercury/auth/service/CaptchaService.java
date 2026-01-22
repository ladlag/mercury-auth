package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.CaptchaChallenge;
import com.mercury.auth.util.KeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CHALLENGE_OPERATOR = "+";
    private static final char CHALLENGE_DELIMITER = ' ';
    private static final int INVALID_ANSWER = -1;
    
    private final StringRedisTemplate redisTemplate;
    private final RateLimitService rateLimitService;
    private final SecureRandom random = new SecureRandom();
    
    @Value("${security.captcha.threshold:3}")
    private long threshold;

    @Value("${security.captcha.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${security.captcha.length:4}")
    private int questionNumberBound;

    /**
     * Creates a new captcha challenge with rate limiting.
     * Rate limiting is applied per user (identified by tenantId + identifier combination)
     * to prevent abuse of captcha generation.
     * 
     * @param action The auth action (used for building rate limit key)
     * @param tenantId The tenant identifier (used for building rate limit key)
     * @param identifier The user identifier (username, email, phone, etc.) used for building rate limit key
     * @return A new CaptchaChallenge with question, image, and expiration
     * @throws ApiException if rate limit is exceeded
     */
    public CaptchaChallenge createChallenge(AuthAction action, String tenantId, String identifier) {
        // Apply rate limiting for captcha generation to prevent abuse
        String rateLimitKey = KeyUtils.buildRateLimitKey(action, tenantId, identifier);
        rateLimitService.check(rateLimitKey);
        
        String question = generateQuestion();
        int evaluated = evaluateQuestion(question);
        if (evaluated == INVALID_ANSWER) {
            evaluated = 0;
        }
        String answer = String.valueOf(evaluated);
        String captchaId = UUID.randomUUID().toString();
        Duration ttl = Duration.ofMinutes(ttlMinutes);
        redisTemplate.opsForValue().set(buildChallengeKey(captchaId), answer, ttl);
        return new CaptchaChallenge(captchaId, question, renderImage(question), ttl.getSeconds());
    }

    /**
     * Verifies a captcha challenge answer.
     * 
     * @param captchaId The unique captcha identifier
     * @param answer The user's answer to the captcha question
     * @return true if the answer is correct, false otherwise
     */
    public boolean verifyChallenge(String captchaId, String answer) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(answer)) {
            return false;
        }
        String key = buildChallengeKey(captchaId);
        String storedAnswer = redisTemplate.opsForValue().get(key);
        if (storedAnswer == null) {
            return false;
        }
        String normalizedAnswer = normalizeAnswer(answer);
        String normalizedStored = normalizeAnswer(storedAnswer);
        boolean matches = normalizedStored.equalsIgnoreCase(normalizedAnswer);
        if (matches) {
            redisTemplate.delete(key);
        }
        return matches;
    }

    public void recordFailure(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        }
    }

    public boolean isRequired(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
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

    public void reset(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        redisTemplate.delete(key);
    }

    public String buildKey(AuthAction action, String tenantId, String identifier) {
        return KeyUtils.buildCaptchaKey(action, tenantId, identifier);
    }

    private String buildChallengeKey(String captchaId) {
        return "captcha:challenge:" + captchaId;
    }

    private String normalizeAnswer(String answer) {
        return answer.trim();
    }

    private String generateQuestion() {
        int bound = Math.max(1, questionNumberBound);
        int left = random.nextInt(bound) + 1;
        int right = random.nextInt(bound) + 1;
        return String.format("%d%c%s%c%d", left, CHALLENGE_DELIMITER, CHALLENGE_OPERATOR, CHALLENGE_DELIMITER, right);
    }

    private int evaluateQuestion(String question) {
        int firstSpace = question.indexOf(CHALLENGE_DELIMITER);
        int secondSpace = question.indexOf(CHALLENGE_DELIMITER, firstSpace + 1);
        int minOperatorEnd = firstSpace + 1 + CHALLENGE_OPERATOR.length();
        if (firstSpace < 0 || secondSpace < minOperatorEnd) {
            return INVALID_ANSWER;
        }
        String operator = question.substring(firstSpace + 1, secondSpace);
        if (!CHALLENGE_OPERATOR.equals(operator)) {
            return INVALID_ANSWER;
        }
        try {
            int left = Integer.parseInt(question.substring(0, firstSpace));
            int right = Integer.parseInt(question.substring(secondSpace + 1));
            return left + right;
        } catch (NumberFormatException parseException) {
            return INVALID_ANSWER;
        }
    }

    private String renderImage(String text) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setFont(new Font("Arial", Font.BOLD, 24));
        for (int i = 0; i < 5; i++) {
            graphics.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            int x1 = random.nextInt(width);
            int y1 = random.nextInt(height);
            int x2 = random.nextInt(width);
            int y2 = random.nextInt(height);
            graphics.drawLine(x1, y1, x2, y2);
        }
        graphics.setColor(new Color(30, 30, 30));
        int xOffset = 10 + random.nextInt(10);
        int yOffset = 25 + random.nextInt(8);
        graphics.drawString(text, xOffset, yOffset);
        graphics.dispose();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception ex) {
            return "";
        }
    }
}

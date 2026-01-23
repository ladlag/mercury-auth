package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final LocalizationService localizationService;
    @Value("${mail.from:no-reply@example.com}")
    private String mailFrom;
    @Value("${security.code.ttl-minutes:10}")
    private long ttlMinutes;
    @Value("${security.code.cooldown-seconds:60}")
    private long cooldownSeconds;
    @Value("${security.code.max-daily-requests:20}")
    private long maxDailyRequests;
    @Value("${security.code.max-verify-attempts:5}")
    private long maxVerifyAttempts;
    private final SecureRandom random = new SecureRandom();
    private boolean emailConfigured = false;

    @PostConstruct
    public void checkEmailConfiguration() {
        try {
            // Check if mail properties are configured
            String host = mailProperties.getHost();
            Integer port = mailProperties.getPort();
            String username = mailProperties.getUsername();
            String password = mailProperties.getPassword();
            
            if (host != null && !host.trim().isEmpty() && 
                port != null && 
                username != null && !username.trim().isEmpty() &&
                password != null && !password.trim().isEmpty()) {
                emailConfigured = true;
                logger.info("Email server configured: host={}, port={}, username={}", host, port, username);
            } else {
                logger.warn("Email server not fully configured. Missing required properties. Email sending will be disabled.");
            }
        } catch (Exception e) {
            logger.warn("Failed to check email configuration: {}", e.getMessage());
            emailConfigured = false;
        }
    }

    public boolean isEmailConfigured() {
        return emailConfigured;
    }

    public String generateCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    public void storeCode(String key, String code, Duration ttl) {
        // Check cooldown period - prevent sending codes too frequently
        String cooldownKey = key + ":cooldown";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, 
                "verification code sent recently, please wait before requesting again");
        }
        
        // Check daily limit - prevent abuse
        String dailyKey = key + ":daily";
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        if (dailyCount != null && dailyCount > maxDailyRequests) {
            throw new ApiException(ErrorCodes.RATE_LIMITED, 
                "daily verification code limit exceeded");
        }
        
        // Store the code
        redisTemplate.opsForValue().set(key, code, ttl);
        
        // Set cooldown period
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
        
        // Reset verification attempt counter
        String attemptsKey = key + ":attempts";
        redisTemplate.delete(attemptsKey);
    }

    public boolean verifyAndConsume(String key, String code) {
        // Check verification attempts to prevent brute force
        String attemptsKey = key + ":attempts";
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            // Set expiration same as code TTL
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(ttlMinutes));
        }
        if (attempts != null && attempts > maxVerifyAttempts) {
            // Too many failed attempts, lock out
            redisTemplate.delete(key);
            throw new ApiException(ErrorCodes.RATE_LIMITED, 
                "too many verification attempts, please request a new code");
        }
        
        String val = redisTemplate.opsForValue().get(key);
        if (val != null && val.equals(code)) {
            // Success: delete code, cooldown, and attempts
            redisTemplate.delete(key);
            redisTemplate.delete(key + ":cooldown");
            redisTemplate.delete(attemptsKey);
            return true;
        }
        return false;
    }

    public void sendEmailCode(String to, String code) {
        if (!emailConfigured) {
            logger.warn("Email server not configured. Cannot send email to: {}", to);
            throw new ApiException(ErrorCodes.EMAIL_SERVICE_UNAVAILABLE, "email service not configured");
        }
        
        try {
            // Get localized subject and body using LocalizationService
            String subject = localizationService.getMessage("email.verification.subject");
            String body = localizationService.getMessage("email.verification.body", new Object[]{code});
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            logger.info("Verification code sent to: {}", to);
        } catch (MailException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new ApiException(ErrorCodes.EMAIL_SERVICE_UNAVAILABLE, "failed to send email");
        }
    }

    public Duration defaultTtl() {
        return Duration.ofMinutes(ttlMinutes);
    }
}

package com.mercury.auth.service;

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
    @Value("${mail.from:no-reply@example.com}")
    private String mailFrom;
    @Value("${security.code.ttl-minutes:10}")
    private long ttlMinutes;
    private final SecureRandom random = new SecureRandom();
    private boolean emailConfigured = false;

    @PostConstruct
    public void checkEmailConfiguration() {
        try {
            // Check if mail properties are configured
            String host = mailProperties.getHost();
            Integer port = mailProperties.getPort();
            
            if (host != null && !host.trim().isEmpty() && port != null) {
                emailConfigured = true;
                logger.info("Email server configured: host={}, port={}", host, port);
            } else {
                logger.warn("Email server not fully configured. Email sending will be disabled.");
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
        redisTemplate.opsForValue().set(key, code, ttl);
    }

    public boolean verifyAndConsume(String key, String code) {
        String val = redisTemplate.opsForValue().get(key);
        if (val != null && val.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    public void sendEmailCode(String to, String code) {
        if (!emailConfigured) {
            logger.warn("Email server not configured. Cannot send email to: {}", to);
            throw new IllegalStateException("Email service not configured");
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject("Your verification code");
            message.setText("Your verification code is: " + code);
            mailSender.send(message);
            logger.info("Verification code sent to: {}", to);
        } catch (MailException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    public Duration defaultTtl() {
        return Duration.ofMinutes(ttlMinutes);
    }
}

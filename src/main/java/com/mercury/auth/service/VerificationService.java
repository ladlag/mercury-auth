package com.mercury.auth.service;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationService.class);
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final MessageSource messageSource;
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
            throw new ApiException(ErrorCodes.EMAIL_SERVICE_UNAVAILABLE, "email service not configured");
        }
        
        try {
            // Get locale from context, default to Chinese
            Locale locale = LocaleContextHolder.getLocale();
            
            // Get localized subject and body
            String subject = messageSource.getMessage("email.verification.subject", null, locale);
            String body = messageSource.getMessage("email.verification.body", new Object[]{code}, locale);
            
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

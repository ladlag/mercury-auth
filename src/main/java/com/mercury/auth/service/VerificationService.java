package com.mercury.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    @Value("${mail.from:no-reply@example.com}")
    private String mailFrom;
    @Value("${security.code.ttl-minutes:10}")
    private long ttlMinutes;
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

    public void sendEmailCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject("Your verification code");
        message.setText("Your verification code is: " + code);
        mailSender.send(message);
    }

    public Duration defaultTtl() {
        return Duration.ofMinutes(ttlMinutes);
    }
}

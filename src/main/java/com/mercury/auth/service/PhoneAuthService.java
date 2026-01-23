package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import com.mercury.auth.util.KeyUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhoneAuthService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneAuthService.class);
    private static final int PHONE_SUFFIX_LENGTH = 8;
    private final VerificationService verificationService;
    private final SmsService smsService;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final CaptchaService captchaService;
    @Value("${security.code.phone-ttl-minutes:5}")
    private long phoneTtlMinutes;

    /**
     * Send verification code to phone for registration or login
     * Returns consistent response to prevent account enumeration
     */
    public User sendPhoneCode(String tenantId, String phone, AuthRequests.VerificationPurpose purpose) {
        tenantService.requireEnabled(tenantId);
        
        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("SEND_PHONE_CODE");
        
        // Apply per-phone rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_SEND_PHONE_CODE, tenantId, phone));
        AuthRequests.VerificationPurpose resolvedPurpose = purpose;
        if (resolvedPurpose == null) {
            resolvedPurpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        
        User user = null;
        boolean shouldSendCode = true;
        
        if (AuthRequests.VerificationPurpose.REGISTER.equals(resolvedPurpose)) {
            // For registration, check if phone already exists
            if (existsByTenantAndPhone(tenantId, phone)) {
                // Don't reveal that phone exists - just don't send code
                logger.warn("sendPhoneCode duplicate phone tenant={} phone={}", tenantId, phone);
                recordFailure(tenantId, null, AuthAction.SEND_PHONE_CODE);
                shouldSendCode = false;
            }
        } else if (AuthRequests.VerificationPurpose.LOGIN.equals(resolvedPurpose)) {
            // For login, check if user exists
            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("tenant_id", tenantId).eq("phone", phone);
            user = userMapper.selectOne(qw);
            if (user == null) {
                // Don't reveal that user doesn't exist - just don't send code
                logger.warn("sendPhoneCode user not found tenant={} phone={}", tenantId, phone);
                recordFailure(tenantId, null, AuthAction.SEND_PHONE_CODE);
                shouldSendCode = false;
            }
        }
        
        if (shouldSendCode) {
            String code = verificationService.generateCode();
            verificationService.storeCode(buildPhoneKey(tenantId, phone), code, Duration.ofMinutes(phoneTtlMinutes));
            // Send SMS with verification code
            smsService.sendVerificationCode(phone, code);
            safeRecord(tenantId, user != null ? user.getId() : null, AuthAction.SEND_PHONE_CODE, true);
        }
        
        // Always return null to prevent account enumeration
        // This ensures the response doesn't reveal whether the account exists or not
        return null;
    }

    public User registerPhone(String tenantId, String phone, String code, String username) {
        tenantService.requireEnabled(tenantId);
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            logger.warn("registerPhone invalid code tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.REGISTER_PHONE);
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        // For brevity reuse password-less path: create user without password
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        if (userMapper.selectCount(wrapper) > 0) {
            logger.warn("registerPhone username exists tenant={} username={}", tenantId, username);
            recordFailure(tenantId, null, AuthAction.REGISTER_PHONE);
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        if (existsByTenantAndPhone(tenantId, phone)) {
            logger.warn("registerPhone phone exists tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.REGISTER_PHONE);
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash("");
        user.setEnabled(true);
        userMapper.insert(user);
        safeRecord(tenantId, user.getId(), AuthAction.REGISTER_PHONE, true);
        return user;
    }

    public AuthResponse loginPhone(String tenantId, String phone, String code, String captchaId, String captcha) {
        tenantService.requireEnabled(tenantId);
        
        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("LOGIN_PHONE");
        
        // Apply per-phone rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_LOGIN_PHONE, tenantId, phone));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_PHONE, tenantId, phone, captchaId, captcha);
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            logger.warn("loginPhone invalid code tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PHONE, tenantId, phone));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginPhone user not found tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PHONE, tenantId, phone));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginPhone user disabled tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, user.getId(), AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PHONE, tenantId, phone));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PHONE, tenantId, phone));
        safeRecord(tenantId, user.getId(), AuthAction.LOGIN_PHONE, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    /**
     * Quick login with phone - register if user doesn't exist, login if exists
     * This combines registration and login for a seamless user experience
     */
    public AuthResponse quickLoginPhone(String tenantId, String phone, String code, String captchaId, String captcha) {
        tenantService.requireEnabled(tenantId);
        
        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("QUICK_LOGIN_PHONE");
        
        // Apply per-phone rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_QUICK_LOGIN_PHONE, tenantId, phone));
        ensureCaptcha(AuthAction.CAPTCHA_QUICK_LOGIN_PHONE, tenantId, phone, captchaId, captcha);
        
        // Verify code first
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            logger.warn("quickLoginPhone invalid code tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.QUICK_LOGIN_PHONE);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_QUICK_LOGIN_PHONE, tenantId, phone));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        
        // Check if user exists
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            // User doesn't exist - register new user
            // Generate username from phone number (use last digits based on PHONE_SUFFIX_LENGTH)
            String baseUsername = "user_" + phone.substring(Math.max(0, phone.length() - PHONE_SUFFIX_LENGTH));
            
            // Check if username already exists, add suffix if needed
            String finalUsername = baseUsername;
            QueryWrapper<User> usernameCheck = new QueryWrapper<>();
            usernameCheck.eq("tenant_id", tenantId).eq("username", finalUsername);
            
            int suffix = 1;
            while (userMapper.selectCount(usernameCheck) > 0) {
                finalUsername = baseUsername + "_" + suffix++;
                usernameCheck.clear();
                usernameCheck.eq("tenant_id", tenantId).eq("username", finalUsername);
            }
            
            user = new User();
            user.setTenantId(tenantId);
            user.setUsername(finalUsername);
            user.setPhone(phone);
            user.setPasswordHash("");
            user.setEnabled(true);
            userMapper.insert(user);
            logger.info("quickLoginPhone registered new user tenant={} phone={} username={}", tenantId, phone, finalUsername);
        } else {
            // User exists - check if enabled
            if (Boolean.FALSE.equals(user.getEnabled())) {
                logger.warn("quickLoginPhone user disabled tenant={} phone={}", tenantId, phone);
                recordFailure(tenantId, user.getId(), AuthAction.QUICK_LOGIN_PHONE);
                captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_QUICK_LOGIN_PHONE, tenantId, phone));
                throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
            }
            logger.info("quickLoginPhone existing user tenant={} phone={} username={}", tenantId, phone, user.getUsername());
        }
        
        // Generate token for the user (new or existing)
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_QUICK_LOGIN_PHONE, tenantId, phone));
        safeRecord(tenantId, user.getId(), AuthAction.QUICK_LOGIN_PHONE, true);
        
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    private String buildPhoneKey(String tenantId, String phone) {
        return "phone:" + tenantId + ":" + phone;
    }

    private boolean existsByTenantAndPhone(String tenantId, String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        return userMapper.selectCount(wrapper) > 0;
    }

    private void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    private void ensureCaptcha(AuthAction action, String tenantId, String identifier, String captchaId, String captcha) {
        String key = KeyUtils.buildCaptchaKey(action, tenantId, identifier);
        if (!captchaService.isRequired(key)) {
            return;
        }
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captcha)) {
            throw new ApiException(ErrorCodes.CAPTCHA_REQUIRED, "captcha required");
        }
        if (!captchaService.verifyChallenge(captchaId, captcha)) {
            captchaService.recordFailure(key);
            throw new ApiException(ErrorCodes.CAPTCHA_INVALID, "captcha invalid");
        }
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ex) {
            // Log failure to record audit log, but don't fail the operation
            logger.error("Failed to record audit log for tenant={} action={}", tenantId, action, ex);
        }
    }
}

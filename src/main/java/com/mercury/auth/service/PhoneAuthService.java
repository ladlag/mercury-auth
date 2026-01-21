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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhoneAuthService {

    private static final Logger logger = LoggerFactory.getLogger(PhoneAuthService.class);
    private final VerificationService verificationService;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final AuthLogService authLogService;
    private final CaptchaService captchaService;
    @Value("${security.code.phone-ttl-minutes:5}")
    private long phoneTtlMinutes;

    public String sendPhoneCode(String tenantId, String phone, AuthRequests.VerificationPurpose purpose) {
        rateLimitService.check(buildRateLimitKey("phone-code", tenantId, phone));
        AuthRequests.VerificationPurpose resolvedPurpose = purpose;
        if (resolvedPurpose == null) {
            resolvedPurpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        if (AuthRequests.VerificationPurpose.REGISTER.equals(resolvedPurpose) && existsByTenantAndPhone(tenantId, phone)) {
            logger.warn("sendPhoneCode duplicate phone tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.SEND_PHONE_CODE);
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        if (AuthRequests.VerificationPurpose.LOGIN.equals(resolvedPurpose) && !existsByTenantAndPhone(tenantId, phone)) {
            logger.warn("sendPhoneCode user not found tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.SEND_PHONE_CODE);
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        String code = verificationService.generateCode();
        verificationService.storeCode(buildPhoneKey(tenantId, phone), code, Duration.ofMinutes(phoneTtlMinutes));
        safeRecord(tenantId, null, AuthAction.SEND_PHONE_CODE, true);
        return "OK"; // stub for SMS sending
    }

    public void registerPhone(String tenantId, String phone, String code, String username) {
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
    }

    public AuthResponse loginPhone(String tenantId, String phone, String code, String captcha) {
        rateLimitService.check(buildRateLimitKey("login-phone", tenantId, phone));
        ensureCaptcha("login-phone", tenantId, phone, captcha);
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            logger.warn("loginPhone invalid code tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(buildCaptchaKey("login-phone", tenantId, phone));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginPhone user not found tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, null, AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(buildCaptchaKey("login-phone", tenantId, phone));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginPhone user disabled tenant={} phone={}", tenantId, phone);
            recordFailure(tenantId, user.getId(), AuthAction.LOGIN_PHONE);
            captchaService.recordFailure(buildCaptchaKey("login-phone", tenantId, phone));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        captchaService.reset(buildCaptchaKey("login-phone", tenantId, phone));
        safeRecord(tenantId, user.getId(), AuthAction.LOGIN_PHONE, true);
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

    private String buildRateLimitKey(String action, String tenantId, String phone) {
        return "rate:" + action + ":" + tenantId + ":" + phone;
    }

    private void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    private void ensureCaptcha(String action, String tenantId, String identifier, String captcha) {
        String key = buildCaptchaKey(action, tenantId, identifier);
        if (captchaService.isRequired(key) && (captcha == null || captcha.trim().isEmpty())) {
            throw new ApiException(ErrorCodes.CAPTCHA_REQUIRED, "captcha required");
        }
    }

    private String buildCaptchaKey(String action, String tenantId, String identifier) {
        return captchaService.buildKey(action, tenantId, identifier);
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

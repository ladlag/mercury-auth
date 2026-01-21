package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthLogRequest;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhoneAuthService {

    private final VerificationService verificationService;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final AuthLogService authLogService;
    @Value("${security.code.phone-ttl-minutes:5}")
    private long phoneTtlMinutes;

    public String sendPhoneCode(String tenantId, String phone, AuthRequests.VerificationPurpose purpose) {
        rateLimitService.check(buildRateLimitKey("phone-code", tenantId, phone));
        AuthRequests.VerificationPurpose resolvedPurpose = purpose;
        if (resolvedPurpose == null) {
            resolvedPurpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        if (AuthRequests.VerificationPurpose.REGISTER.equals(resolvedPurpose) && existsByTenantAndPhone(tenantId, phone)) {
            recordFailure(tenantId, null, "SEND_PHONE_CODE");
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        if (AuthRequests.VerificationPurpose.LOGIN.equals(resolvedPurpose) && !existsByTenantAndPhone(tenantId, phone)) {
            recordFailure(tenantId, null, "SEND_PHONE_CODE");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        String code = verificationService.generateCode();
        verificationService.storeCode(buildPhoneKey(tenantId, phone), code, Duration.ofMinutes(phoneTtlMinutes));
        safeRecord(buildLog(tenantId, null, "SEND_PHONE_CODE", true));
        return "OK"; // stub for SMS sending
    }

    public void registerPhone(String tenantId, String phone, String code, String username) {
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            recordFailure(tenantId, null, "REGISTER_PHONE");
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        // For brevity reuse password-less path: create user without password
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        if (userMapper.selectCount(wrapper) > 0) {
            recordFailure(tenantId, null, "REGISTER_PHONE");
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        if (existsByTenantAndPhone(tenantId, phone)) {
            recordFailure(tenantId, null, "REGISTER_PHONE");
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash("");
        user.setEnabled(true);
        userMapper.insert(user);
        safeRecord(buildLog(tenantId, user.getId(), "REGISTER_PHONE", true));
    }

    public AuthResponse loginPhone(String tenantId, String phone, String code) {
        rateLimitService.check(buildRateLimitKey("login-phone", tenantId, phone));
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            recordFailure(tenantId, null, "LOGIN_PHONE");
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            recordFailure(tenantId, null, "LOGIN_PHONE");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            recordFailure(tenantId, user.getId(), "LOGIN_PHONE");
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        safeRecord(buildLog(tenantId, user.getId(), "LOGIN_PHONE", true));
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

    private AuthLogRequest buildLog(String tenantId, Long userId, String action, boolean success) {
        AuthLogRequest request = new AuthLogRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setAction(action);
        request.setSuccess(success);
        return request;
    }

    private void recordFailure(String tenantId, Long userId, String action) {
        safeRecord(buildLog(tenantId, userId, action, false));
    }

    private void safeRecord(AuthLogRequest request) {
        try {
            authLogService.record(request);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

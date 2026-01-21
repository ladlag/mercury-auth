package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    @Value("${security.code.phone-ttl-minutes:5}")
    private long phoneTtlMinutes;

    public String sendPhoneCode(String tenantId, String phone, AuthRequests.VerificationPurpose purpose) {
        rateLimitService.check(buildRateLimitKey("phone-code", tenantId, phone));
        AuthRequests.VerificationPurpose resolvedPurpose = purpose;
        if (resolvedPurpose == null) {
            resolvedPurpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        if (AuthRequests.VerificationPurpose.REGISTER.equals(resolvedPurpose) && existsByTenantAndPhone(tenantId, phone)) {
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        if (AuthRequests.VerificationPurpose.LOGIN.equals(resolvedPurpose) && !existsByTenantAndPhone(tenantId, phone)) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        String code = verificationService.generateCode();
        verificationService.storeCode(buildPhoneKey(tenantId, phone), code, Duration.ofMinutes(phoneTtlMinutes));
        return "OK"; // stub for SMS sending
    }

    public void registerPhone(String tenantId, String phone, String code, String username) {
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        // For brevity reuse password-less path: create user without password
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        if (existsByTenantAndPhone(tenantId, phone)) {
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPhone(phone);
        user.setPasswordHash("");
        user.setEnabled(true);
        userMapper.insert(user);
    }

    public AuthResponse loginPhone(String tenantId, String phone, String code) {
        rateLimitService.check(buildRateLimitKey("login-phone", tenantId, phone));
        if (!verificationService.verifyAndConsume(buildPhoneKey(tenantId, phone), code)) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
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
}

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
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PhoneAuthService {

    private final VerificationService verificationService;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public String sendPhoneCode(String tenantId, String phone) {
        String code = verificationService.generateCode();
        verificationService.storeCode(buildPhoneKey(tenantId, phone), code, Duration.ofMinutes(5));
        return code; // stub for SMS sending
    }

    public void registerPhone(String tenantId, String phone, String code, String username) {
        if (!verificationService.verify(buildPhoneKey(tenantId, phone), code)) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        // For brevity reuse password-less path: create user without password
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
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
        if (!verificationService.verify(buildPhoneKey(tenantId, phone), code)) {
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
}

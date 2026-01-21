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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;

    public void registerPassword(AuthRequests.PasswordRegister req) {
        if (!StringUtils.hasText(req.getPassword()) || !req.getPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        User user = new User();
        user.setTenantId(req.getTenantId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        userMapper.insert(user);
    }

    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    public String sendEmailCode(AuthRequests.SendEmailCode req) {
        String code = verificationService.generateCode();
        verificationService.storeCode(buildEmailKey(req.getTenantId(), req.getEmail()), code, verificationService.defaultTtl());
        verificationService.sendEmailCode(req.getEmail(), code);
        return code;
    }

    public void registerEmail(AuthRequests.EmailRegister req) {
        if (!verificationService.verify(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        AuthRequests.PasswordRegister pr = new AuthRequests.PasswordRegister();
        pr.setTenantId(req.getTenantId());
        pr.setUsername(req.getUsername());
        pr.setPassword(req.getPassword());
        pr.setConfirmPassword(req.getConfirmPassword());
        pr.setEmail(req.getEmail());
        registerPassword(pr);
    }

    public AuthResponse loginEmail(AuthRequests.EmailLogin req) {
        if (!verificationService.verify(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    private String buildEmailKey(String tenantId, String email) {
        return "email:" + tenantId + ":" + email;
    }
}

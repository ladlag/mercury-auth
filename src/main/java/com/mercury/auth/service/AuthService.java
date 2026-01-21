package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthLogRequest;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;

    public void registerPassword(AuthRequests.PasswordRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        User user;
        try {
            user = createUser(req);
        } catch (ApiException ex) {
            recordFailure(req.getTenantId(), null, "REGISTER_PASSWORD");
            throw ex;
        }
        safeRecord(buildLog(req.getTenantId(), user.getId(), "REGISTER_PASSWORD", true));
    }

    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey("login-password", req.getTenantId(), req.getUsername()));
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            recordFailure(req.getTenantId(), null, "LOGIN_PASSWORD");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            recordFailure(req.getTenantId(), user.getId(), "LOGIN_PASSWORD");
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            recordFailure(req.getTenantId(), user.getId(), "LOGIN_PASSWORD");
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        safeRecord(buildLog(req.getTenantId(), user.getId(), "LOGIN_PASSWORD", true));
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    public String sendEmailCode(AuthRequests.SendEmailCode req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey("email-code", req.getTenantId(), req.getEmail()));
        AuthRequests.VerificationPurpose purpose = req.getPurpose();
        if (purpose == null) {
            purpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        if (AuthRequests.VerificationPurpose.REGISTER.equals(purpose)
                && existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            recordFailure(req.getTenantId(), null, "SEND_EMAIL_CODE");
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }
        if (AuthRequests.VerificationPurpose.LOGIN.equals(purpose)
                && !existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            recordFailure(req.getTenantId(), null, "SEND_EMAIL_CODE");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        String code = verificationService.generateCode();
        verificationService.storeCode(buildEmailKey(req.getTenantId(), req.getEmail()), code, verificationService.defaultTtl());
        verificationService.sendEmailCode(req.getEmail(), code);
        safeRecord(buildLog(req.getTenantId(), null, "SEND_EMAIL_CODE", true));
        return "OK";
    }

    public void registerEmail(AuthRequests.EmailRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        if (!verificationService.verifyAndConsume(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            recordFailure(req.getTenantId(), null, "REGISTER_EMAIL");
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        AuthRequests.PasswordRegister pr = new AuthRequests.PasswordRegister();
        pr.setTenantId(req.getTenantId());
        pr.setUsername(req.getUsername());
        pr.setPassword(req.getPassword());
        pr.setConfirmPassword(req.getConfirmPassword());
        pr.setEmail(req.getEmail());
        User user;
        try {
            user = createUser(pr);
        } catch (ApiException ex) {
            recordFailure(req.getTenantId(), null, "REGISTER_EMAIL");
            throw ex;
        }
        safeRecord(buildLog(req.getTenantId(), user.getId(), "REGISTER_EMAIL", true));
    }

    public AuthResponse loginEmail(AuthRequests.EmailLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey("login-email", req.getTenantId(), req.getEmail()));
        if (!verificationService.verifyAndConsume(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            recordFailure(req.getTenantId(), null, "LOGIN_EMAIL");
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            recordFailure(req.getTenantId(), null, "LOGIN_EMAIL");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            recordFailure(req.getTenantId(), user.getId(), "LOGIN_EMAIL");
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        safeRecord(buildLog(req.getTenantId(), user.getId(), "LOGIN_EMAIL", true));
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    public TokenVerifyResponse verifyToken(AuthRequests.TokenVerify req) {
        return tokenService.verifyToken(req.getTenantId(), req.getToken());
    }

    public AuthResponse refreshToken(AuthRequests.TokenRefresh req) {
        tenantService.requireEnabled(req.getTenantId());
        return tokenService.refreshToken(req.getTenantId(), req.getToken());
    }

    public void logout(AuthRequests.TokenLogout req) {
        tokenService.blacklistToken(req.getTenantId(), req.getToken());
    }

    public void updateUserStatus(AuthRequests.UserStatusUpdate req) {
        tenantService.requireEnabled(req.getTenantId());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        user.setEnabled(req.isEnabled());
        userMapper.updateById(user);
        safeRecord(buildLog(req.getTenantId(), user.getId(), "UPDATE_USER_STATUS", true));
    }

    public void changePassword(AuthRequests.ChangePassword req) {
        tenantService.requireEnabled(req.getTenantId());
        if (!StringUtils.hasText(req.getNewPassword()) || !req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        safeRecord(buildLog(req.getTenantId(), user.getId(), "CHANGE_PASSWORD", true));
    }

    private AuthLogRequest buildLog(String tenantId, Long userId, String action, boolean success) {
        AuthLogRequest request = new AuthLogRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setAction(action);
        request.setSuccess(success);
        return request;
    }

    private String buildEmailKey(String tenantId, String email) {
        return "email:" + tenantId + ":" + email;
    }

    private boolean existsByTenantAndEmail(String tenantId, String email) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("email", email);
        return userMapper.selectCount(wrapper) > 0;
    }

    private boolean existsByTenantAndPhone(String tenantId, String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("phone", phone);
        return userMapper.selectCount(wrapper) > 0;
    }

    private String buildRateLimitKey(String action, String tenantId, String identifier) {
        return "rate:" + action + ":" + tenantId + ":" + identifier;
    }

    private void recordFailure(String tenantId, Long userId, String action) {
        safeRecord(buildLog(tenantId, userId, action, false));
    }

    private User createUser(AuthRequests.PasswordRegister req) {
        if (!StringUtils.hasText(req.getPassword()) || !req.getPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        if (StringUtils.hasText(req.getEmail()) && existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }
        if (StringUtils.hasText(req.getPhone()) && existsByTenantAndPhone(req.getTenantId(), req.getPhone())) {
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }
        User user = new User();
        user.setTenantId(req.getTenantId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        userMapper.insert(user);
        return user;
    }

    private void safeRecord(AuthLogRequest request) {
        try {
            authLogService.record(request);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

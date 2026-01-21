package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final RateLimitService rateLimitService;
    private final TokenService tokenService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final CaptchaService captchaService;

    public void registerPassword(AuthRequests.PasswordRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        User user;
        try {
            user = createUser(req);
        } catch (ApiException ex) {
            logger.warn("registerPassword failed tenant={} username={} code={}",
                    req.getTenantId(), req.getUsername(), ex.getCode());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_PASSWORD);
            throw ex;
        }
        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_PASSWORD, true);
    }

    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey(AuthAction.RATE_LIMIT_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername(), req.getCaptcha());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginPassword user not found tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginPassword user disabled tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            logger.warn("loginPassword bad credentials tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    public String sendEmailCode(AuthRequests.SendEmailCode req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey(AuthAction.RATE_LIMIT_SEND_EMAIL_CODE, req.getTenantId(), req.getEmail()));
        AuthRequests.VerificationPurpose purpose = req.getPurpose();
        if (purpose == null) {
            purpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        if (AuthRequests.VerificationPurpose.REGISTER.equals(purpose)
                && existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            logger.warn("sendEmailCode duplicate email tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.SEND_EMAIL_CODE);
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }
        if (AuthRequests.VerificationPurpose.LOGIN.equals(purpose)
                && !existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            logger.warn("sendEmailCode user not found tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.SEND_EMAIL_CODE);
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        String code = verificationService.generateCode();
        verificationService.storeCode(buildEmailKey(req.getTenantId(), req.getEmail()), code, verificationService.defaultTtl());
        verificationService.sendEmailCode(req.getEmail(), code);
        safeRecord(req.getTenantId(), null, AuthAction.SEND_EMAIL_CODE, true);
        return "OK";
    }

    public void registerEmail(AuthRequests.EmailRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        if (!verificationService.verifyAndConsume(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            logger.warn("registerEmail invalid code tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
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
            logger.warn("registerEmail failed tenant={} username={} code={}",
                    req.getTenantId(), req.getUsername(), ex.getCode());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
            throw ex;
        }
        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_EMAIL, true);
    }

    public AuthResponse loginEmail(AuthRequests.EmailLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(buildRateLimitKey(AuthAction.RATE_LIMIT_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail(), req.getCaptcha());
        if (!verificationService.verifyAndConsume(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            logger.warn("loginEmail invalid code tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginEmail user not found tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginEmail user disabled tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    private void ensureCaptcha(AuthAction action, String tenantId, String identifier, String captcha) {
        String key = buildCaptchaKey(action, tenantId, identifier);
        if (captchaService.isRequired(key) && (captcha == null || captcha.trim().isEmpty())) {
            throw new ApiException(ErrorCodes.CAPTCHA_REQUIRED, "captcha required");
        }
    }

    private String buildCaptchaKey(AuthAction action, String tenantId, String identifier) {
        return captchaService.buildKey(action, tenantId, identifier);
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
            logger.warn("updateUserStatus user not found tenant={} username={}", req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        user.setEnabled(req.isEnabled());
        userMapper.updateById(user);
        safeRecord(req.getTenantId(), user.getId(), AuthAction.UPDATE_USER_STATUS, true);
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
            logger.warn("changePassword user not found tenant={} username={}", req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            logger.warn("changePassword bad credentials tenant={} username={}", req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        safeRecord(req.getTenantId(), user.getId(), AuthAction.CHANGE_PASSWORD, true);
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

    private String buildRateLimitKey(AuthAction action, String tenantId, String identifier) {
        return "rate:" + action.name() + ":" + tenantId + ":" + identifier;
    }

    private void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    private User createUser(AuthRequests.PasswordRegister req) {
        if (!StringUtils.hasText(req.getPassword()) || !req.getPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            logger.warn("createUser duplicate username tenant={} username={}", req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        if (StringUtils.hasText(req.getEmail()) && existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            logger.warn("createUser duplicate email tenant={} email={}", req.getTenantId(), req.getEmail());
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }
        if (StringUtils.hasText(req.getPhone()) && existsByTenantAndPhone(req.getTenantId(), req.getPhone())) {
            logger.warn("createUser duplicate phone tenant={} phone={}", req.getTenantId(), req.getPhone());
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

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

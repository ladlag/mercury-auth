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
import com.mercury.auth.util.KeyUtils;
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

    public User registerPassword(AuthRequests.PasswordRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        User user;
        try {
            user = createUser(req);
        } catch (ApiException ex) {
            logger.warn("registerPassword failed tenant={} username={} code={}",
                    req.getTenantId(), req.getUsername(), ex.getCode().name());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_PASSWORD);
            throw ex;
        }
        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_PASSWORD, true);
        return user;
    }

    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername(), req.getCaptchaId(), req.getCaptcha());
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginPassword user not found tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginPassword user disabled tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            logger.warn("loginPassword bad credentials tenant={} username={}", req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    public String sendEmailCode(AuthRequests.SendEmailCode req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_SEND_EMAIL_CODE, req.getTenantId(), req.getEmail()));
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

    public User registerEmail(AuthRequests.EmailRegister req) {
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
                    req.getTenantId(), req.getUsername(), ex.getCode().name());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
            throw ex;
        }
        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_EMAIL, true);
        return user;
    }

    public AuthResponse loginEmail(AuthRequests.EmailLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        rateLimitService.check(KeyUtils.buildRateLimitKey(AuthAction.RATE_LIMIT_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail(), req.getCaptchaId(), req.getCaptcha());
        if (!verificationService.verifyAndConsume(buildEmailKey(req.getTenantId(), req.getEmail()), req.getCode())) {
            logger.warn("loginEmail invalid code tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("loginEmail user not found tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginEmail user disabled tenant={} email={}", req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
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

    public void forgotPassword(AuthRequests.ForgotPassword req) {
        tenantService.requireEnabled(req.getTenantId());
        
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", req.getTenantId());
        qw.eq("email", req.getEmail());
        User user = userMapper.selectOne(qw);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        String code = verificationService.generateCode();
        String key = KeyUtils.passwordResetCodeKey(req.getTenantId(), req.getEmail());
        verificationService.storeCode(key, code, verificationService.defaultTtl());
        verificationService.sendEmailCode(req.getEmail(), code);
        
        logger.info("Password reset code sent tenant={} email={}", req.getTenantId(), req.getEmail());
    }

    public void resetPassword(AuthRequests.ResetPassword req) {
        tenantService.requireEnabled(req.getTenantId());
        
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        
        String key = KeyUtils.passwordResetCodeKey(req.getTenantId(), req.getEmail());
        if (!verificationService.verifyAndConsume(key, req.getCode())) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", req.getTenantId());
        qw.eq("email", req.getEmail());
        User user = userMapper.selectOne(qw);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        
        logger.info("Password reset successful tenant={} email={}", req.getTenantId(), req.getEmail());
        safeRecord(req.getTenantId(), user.getId(), AuthAction.PASSWORD_RESET, true);
    }

    public void verifyEmailAfterRegister(AuthRequests.VerifyEmailAfterRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        
        String key = KeyUtils.emailVerificationKey(req.getTenantId(), req.getEmail());
        if (!verificationService.verifyAndConsume(key, req.getCode())) {
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", req.getTenantId());
        qw.eq("email", req.getEmail());
        User user = userMapper.selectOne(qw);
        
        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        // Mark email as verified (could add a field to User entity if needed)
        logger.info("Email verified tenant={} email={}", req.getTenantId(), req.getEmail());
        safeRecord(req.getTenantId(), user.getId(), AuthAction.EMAIL_VERIFY, true);
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

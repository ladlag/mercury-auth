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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service for password-based authentication operations including
 * registration, login, password change, and password reset.
 */
@Service
@RequiredArgsConstructor
public class PasswordAuthService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordAuthService.class);

    /**
     * RFC 5322 simplified email validation pattern
     */
    private static final String EMAIL_PATTERN = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final RateLimitService rateLimitService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final CaptchaService captchaService;

    /**
     * Register a new user with username and password
     */
    public User registerPassword(AuthRequests.PasswordRegister req) {
        tenantService.requireEnabled(req.getTenantId());

        // Validate password confirmation
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }

        // Check for duplicate username
        if (existsByTenantAndUsername(req.getTenantId(), req.getUsername())) {
            logger.warn("registerPassword duplicate username tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_PASSWORD);
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }

        // Auto-populate email if username is a valid email address
        if ((req.getEmail() == null || req.getEmail().trim().isEmpty()) && isValidEmail(req.getUsername())) {
            req.setEmail(req.getUsername());
            logger.info("registerPassword auto-populated email from username tenant={} email={}",
                    req.getTenantId(), req.getUsername());
        }

        // Check for duplicate email if provided
        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()
                && existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            logger.warn("registerPassword duplicate email tenant={} email={}",
                    req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_PASSWORD);
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }

        // Check for duplicate phone if provided
        if (req.getPhone() != null && !req.getPhone().trim().isEmpty()
                && existsByTenantAndPhone(req.getTenantId(), req.getPhone())) {
            logger.warn("registerPassword duplicate phone tenant={} phone={}",
                    req.getTenantId(), req.getPhone());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_PASSWORD);
            throw new ApiException(ErrorCodes.DUPLICATE_PHONE, "phone exists");
        }

        // Create user
        User user = new User();
        user.setTenantId(req.getTenantId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        userMapper.insert(user);

        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_PASSWORD, true);
        return user;
    }

    /**
     * Login with username and password
     */
    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        tenantService.requireEnabled(req.getTenantId());

        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("LOGIN_PASSWORD");

        // Apply per-username rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(
                AuthAction.RATE_LIMIT_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(),
                req.getUsername(), req.getCaptchaId(), req.getCaptcha());

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            logger.warn("loginPassword user not found tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                    AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginPassword user disabled tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                    AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            logger.warn("loginPassword bad credentials tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                    AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }

        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(
                AuthAction.CAPTCHA_LOGIN_PASSWORD, req.getTenantId(), req.getUsername()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_PASSWORD, true);

        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    /**
     * Change user's password (requires old password verification)
     */
    public User changePassword(AuthRequests.ChangePassword req) {
        tenantService.requireEnabled(req.getTenantId());

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            logger.warn("changePassword old password mismatch tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPasswordHash())) {
            logger.warn("changePassword same as old tenant={} username={}",
                    req.getTenantId(), req.getUsername());
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);

        logger.info("Password changed tenant={} username={}", req.getTenantId(), req.getUsername());
        safeRecord(req.getTenantId(), user.getId(), AuthAction.CHANGE_PASSWORD, true);
        return user;
    }

    /**
     * Send password reset code to user's email
     */
    public User forgotPassword(AuthRequests.ForgotPassword req) {
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
        return user;
    }

    /**
     * Reset password using verification code
     */
    public User resetPassword(AuthRequests.ResetPassword req) {
        tenantService.requireEnabled(req.getTenantId());

        // Compare passwords - timing attacks aren't a concern for user input validation
        // Both values are user-provided plaintext, not secrets or hashed values
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
        return user;
    }

    // Helper methods

    private void ensureCaptcha(AuthAction action, String tenantId, String identifier,
                               String captchaId, String captcha) {
        String key = KeyUtils.buildCaptchaKey(action, tenantId, identifier);
        if (captchaService.isRequired(key)) {
            if (captchaId == null || captcha == null) {
                logger.warn("captcha required action={} tenant={} identifier={}",
                        action, tenantId, identifier);
                throw new ApiException(ErrorCodes.CAPTCHA_REQUIRED, "captcha required");
            }
            if (!captchaService.verifyChallenge(captchaId, captcha)) {
                logger.warn("captcha invalid action={} tenant={} identifier={}",
                        action, tenantId, identifier);
                throw new ApiException(ErrorCodes.CAPTCHA_INVALID, "captcha invalid");
            }
        }
    }

    private boolean existsByTenantAndUsername(String tenantId, String username) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("username", username);
        return userMapper.selectCount(qw) > 0;
    }

    private boolean existsByTenantAndEmail(String tenantId, String email) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("email", email);
        return userMapper.selectCount(qw) > 0;
    }

    private boolean existsByTenantAndPhone(String tenantId, String phone) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("phone", phone);
        return userMapper.selectCount(qw) > 0;
    }

    private void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ex) {
            logger.error("Failed to record audit log for tenant={} action={}", tenantId, action, ex);
        }
    }

    /**
     * Check if a string is a valid email address
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.matches(EMAIL_PATTERN);
    }
}

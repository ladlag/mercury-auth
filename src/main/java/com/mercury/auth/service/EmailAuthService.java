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
 * Service for email-based authentication operations including
 * sending verification codes, registration, login, and email verification.
 */
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private static final Logger logger = LoggerFactory.getLogger(EmailAuthService.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationService verificationService;
    private final RateLimitService rateLimitService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final CaptchaService captchaService;
    private final PasswordEncryptionService passwordEncryptionService;

    /**
     * Send verification code to email for registration or login
     * Returns consistent response to prevent account enumeration
     */
    public User sendEmailCode(AuthRequests.SendEmailCode req) {
        tenantService.requireEnabled(req.getTenantId());
        
        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("SEND_EMAIL_CODE");
        
        // Apply per-email rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(
            AuthAction.RATE_LIMIT_SEND_EMAIL_CODE, req.getTenantId(), req.getEmail()));
        
        AuthRequests.VerificationPurpose purpose = req.getPurpose();
        if (purpose == null) {
            purpose = AuthRequests.VerificationPurpose.REGISTER;
        }
        
        User user = null;
        boolean shouldSendCode = true;
        
        if (AuthRequests.VerificationPurpose.REGISTER.equals(purpose)) {
            // For registration, check if email already exists
            if (existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
                // Don't reveal that email exists - just don't send code
                logger.warn("sendEmailCode duplicate email tenant={} email={}", 
                    req.getTenantId(), req.getEmail());
                recordFailure(req.getTenantId(), null, AuthAction.SEND_EMAIL_CODE);
                shouldSendCode = false;
            }
        } else if (AuthRequests.VerificationPurpose.LOGIN.equals(purpose)) {
            // For login, check if user exists
            QueryWrapper<User> qw = new QueryWrapper<>();
            qw.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
            user = userMapper.selectOne(qw);
            if (user == null) {
                // Don't reveal that user doesn't exist - just don't send code
                logger.warn("sendEmailCode user not found tenant={} email={}", 
                    req.getTenantId(), req.getEmail());
                recordFailure(req.getTenantId(), null, AuthAction.SEND_EMAIL_CODE);
                shouldSendCode = false;
            }
        }
        
        if (shouldSendCode) {
            String code = verificationService.generateCode();
            String key = "email:" + req.getTenantId() + ":" + req.getEmail();
            verificationService.storeCode(key, code, verificationService.defaultTtl());
            verificationService.sendEmailCode(req.getEmail(), code);
            safeRecord(req.getTenantId(), user != null ? user.getId() : null, AuthAction.SEND_EMAIL_CODE, true);
        }
        
        // Always return null to prevent account enumeration
        // This ensures the response doesn't reveal whether the account exists or not
        return null;
    }

    /**
     * Register a new user with email and verification code
     */
    public User registerEmail(AuthRequests.EmailRegister req) {
        tenantService.requireEnabled(req.getTenantId());
        
        // Decrypt passwords if encrypted (based on tenant configuration)
        String password;
        String confirmPassword;
        try {
            password = passwordEncryptionService.processPassword(req.getTenantId(), req.getPassword());
            confirmPassword = passwordEncryptionService.processPassword(req.getTenantId(), req.getConfirmPassword());
        } catch (Exception e) {
            logger.error("Failed to decrypt password tenant={} email={}", 
                    req.getTenantId(), req.getEmail(), e);
            throw new ApiException(ErrorCodes.BAD_CREDENTIALS, "invalid password format");
        }
        
        if (!password.equals(confirmPassword)) {
            throw new ApiException(ErrorCodes.PASSWORD_MISMATCH, "password mismatch");
        }
        
        String key = "email:" + req.getTenantId() + ":" + req.getEmail();
        if (!verificationService.verifyAndConsume(key, req.getCode())) {
            logger.warn("registerEmail invalid code tenant={} email={}", 
                req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        
        if (existsByTenantAndUsername(req.getTenantId(), req.getUsername())) {
            logger.warn("registerEmail duplicate username tenant={} username={}", 
                req.getTenantId(), req.getUsername());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
            throw new ApiException(ErrorCodes.DUPLICATE_USERNAME, "username exists");
        }
        
        if (existsByTenantAndEmail(req.getTenantId(), req.getEmail())) {
            logger.warn("registerEmail duplicate email tenant={} email={}", 
                req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.REGISTER_EMAIL);
            throw new ApiException(ErrorCodes.DUPLICATE_EMAIL, "email exists");
        }
        
        User user = new User();
        user.setTenantId(req.getTenantId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        userMapper.insert(user);
        
        safeRecord(req.getTenantId(), user.getId(), AuthAction.REGISTER_EMAIL, true);
        return user;
    }

    /**
     * Login with email and verification code
     */
    public AuthResponse loginEmail(AuthRequests.EmailLogin req) {
        tenantService.requireEnabled(req.getTenantId());
        
        // Apply IP-based rate limiting
        rateLimitService.checkIpRateLimit("LOGIN_EMAIL");
        
        // Apply per-email rate limiting
        rateLimitService.check(KeyUtils.buildRateLimitKey(
            AuthAction.RATE_LIMIT_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        ensureCaptcha(AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), 
            req.getEmail(), req.getCaptchaId(), req.getCaptcha());
        
        String key = "email:" + req.getTenantId() + ":" + req.getEmail();
        if (!verificationService.verifyAndConsume(key, req.getCode())) {
            logger.warn("loginEmail invalid code tenant={} email={}", 
                req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.INVALID_CODE, "invalid code");
        }
        
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("tenant_id", req.getTenantId()).eq("email", req.getEmail());
        User user = userMapper.selectOne(qw);
        
        if (user == null) {
            logger.warn("loginEmail user not found tenant={} email={}", 
                req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), null, AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("loginEmail user disabled tenant={} email={}", 
                req.getTenantId(), req.getEmail());
            recordFailure(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL);
            captchaService.recordFailure(KeyUtils.buildCaptchaKey(
                AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        captchaService.reset(KeyUtils.buildCaptchaKey(
            AuthAction.CAPTCHA_LOGIN_EMAIL, req.getTenantId(), req.getEmail()));
        safeRecord(req.getTenantId(), user.getId(), AuthAction.LOGIN_EMAIL, true);
        
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }

    /**
     * Verify email address after registration
     */
    public User verifyEmailAfterRegister(AuthRequests.VerifyEmailAfterRegister req) {
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
        
        logger.info("Email verified tenant={} email={}", req.getTenantId(), req.getEmail());
        safeRecord(req.getTenantId(), user.getId(), AuthAction.EMAIL_VERIFY, true);
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
}

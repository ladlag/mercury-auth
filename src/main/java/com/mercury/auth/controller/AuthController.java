package com.mercury.auth.controller;

import com.mercury.auth.dto.ApiResponse;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.BaseAuthResponse;
import com.mercury.auth.dto.CaptchaChallenge;
import com.mercury.auth.dto.PublicKeyResponse;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.*;
import com.mercury.auth.util.XssSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordAuthService passwordAuthService;
    private final EmailAuthService emailAuthService;
    private final PhoneAuthService phoneAuthService;
    private final WeChatAuthService weChatAuthService;
    private final TokenService tokenService;
    private final UserService userService;
    private final CaptchaService captchaService;
    private final RsaKeyService rsaKeyService;

    // Public key endpoint for password encryption
    
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<PublicKeyResponse>> getPublicKey(@org.springframework.web.bind.annotation.RequestHeader("X-Tenant-Id") String tenantId) {
        PublicKeyResponse data = PublicKeyResponse.builder()
                .publicKey(rsaKeyService.getPublicKeyBase64(tenantId))
                .encryptionEnabled(rsaKeyService.isEncryptionEnabled(tenantId))
                .keySize(rsaKeyService.isEncryptionEnabled(tenantId) ? 2048 : 0)
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    // Password-based authentication endpoints
    
    @PostMapping("/register-password")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> registerPassword(@Validated @RequestBody AuthRequests.PasswordRegister req) {
        User user = passwordAuthService.registerPassword(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/login-password")
    public ResponseEntity<ApiResponse<AuthResponse>> loginPassword(@Validated @RequestBody AuthRequests.PasswordLogin req) {
        return ResponseEntity.ok(ApiResponse.success(passwordAuthService.loginPassword(req)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> changePassword(@Validated @RequestBody AuthRequests.ChangePassword req) {
        User user = passwordAuthService.changePassword(req);
        
        // Revoke all existing tokens for this user after password change (security enhancement)
        // This forces the user to re-login on all devices
        tokenService.revokeAllUserTokens(req.getTenantId(), user.getId());
        
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> forgotPassword(@Validated @RequestBody AuthRequests.ForgotPassword req) {
        User user = passwordAuthService.forgotPassword(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> resetPassword(@Validated @RequestBody AuthRequests.ResetPassword req) {
        User user = passwordAuthService.resetPassword(req);
        
        // Revoke all existing tokens for this user after password reset (security enhancement)
        // This forces the user to re-login on all devices
        tokenService.revokeAllUserTokens(req.getTenantId(), user.getId());
        
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    // Email-based authentication endpoints
    
    @PostMapping("/send-email-code")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> sendEmailCode(@Validated @RequestBody AuthRequests.SendEmailCode req) {
        User user = emailAuthService.sendEmailCode(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/register-email")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> registerEmail(@Validated @RequestBody AuthRequests.EmailRegister req) {
        User user = emailAuthService.registerEmail(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/login-email")
    public ResponseEntity<ApiResponse<AuthResponse>> loginEmail(@Validated @RequestBody AuthRequests.EmailLogin req) {
        return ResponseEntity.ok(ApiResponse.success(emailAuthService.loginEmail(req)));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> verifyEmail(@Validated @RequestBody AuthRequests.VerifyEmailAfterRegister req) {
        User user = emailAuthService.verifyEmailAfterRegister(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    // Phone-based authentication endpoints
    
    @PostMapping("/send-phone-code")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> sendPhoneCode(@Validated @RequestBody AuthRequests.SendPhoneCode req) {
        User user = phoneAuthService.sendPhoneCode(req.getTenantId(), req.getPhone(), req.getPurpose());
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/register-phone")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> registerPhone(@Validated @RequestBody AuthRequests.PhoneRegister req) {
        User user = phoneAuthService.registerPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getUsername());
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    @PostMapping("/login-phone")
    public ResponseEntity<ApiResponse<AuthResponse>> loginPhone(@Validated @RequestBody AuthRequests.PhoneLogin req) {
        AuthResponse authResponse = phoneAuthService.loginPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getCaptchaId(), req.getCaptcha());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    @PostMapping("/quick-login-phone")
    public ResponseEntity<ApiResponse<AuthResponse>> quickLoginPhone(@Validated @RequestBody AuthRequests.PhoneQuickLogin req) {
        AuthResponse authResponse = phoneAuthService.quickLoginPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getCaptchaId(), req.getCaptcha());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    // WeChat authentication endpoints
    
    @PostMapping("/wechat-login")
    public ResponseEntity<ApiResponse<AuthResponse>> wechatLogin(@Validated @RequestBody AuthRequests.WeChatLogin req) {
        AuthResponse authResponse = weChatAuthService.loginOrRegister(req.getTenantId(), req.getOpenId(), req.getUsername());
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    // Token management endpoints
    
    @PostMapping("/verify-token")
    public ResponseEntity<ApiResponse<TokenVerifyResponse>> verifyToken(@Validated @RequestBody AuthRequests.TokenVerify req) {
        return ResponseEntity.ok(ApiResponse.success(tokenService.verifyToken(req)));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Validated @RequestBody AuthRequests.TokenRefresh req) {
        return ResponseEntity.ok(ApiResponse.success(tokenService.refreshToken(req)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> logout(@Validated @RequestBody AuthRequests.TokenLogout req) {
        User user = tokenService.logout(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    // User management endpoints
    
    @PostMapping("/user-status")
    public ResponseEntity<ApiResponse<BaseAuthResponse>> updateUserStatus(@Validated @RequestBody AuthRequests.UserStatusUpdate req) {
        User user = userService.updateUserStatus(req);
        return ResponseEntity.ok(ApiResponse.success(buildBaseAuthResponse(user, req.getTenantId())));
    }

    // Captcha endpoints
    
    @PostMapping("/captcha")
    public ResponseEntity<ApiResponse<CaptchaChallenge>> getCaptcha(@Validated @RequestBody AuthRequests.CaptchaRequest req) {
        AuthAction action;
        try {
            action = AuthAction.valueOf(req.getAction());
        } catch (IllegalArgumentException ex) {
            StringBuilder validActions = new StringBuilder();
            for (AuthAction value : AuthAction.values()) {
                if (validActions.length() > 0) {
                    validActions.append(", ");
                }
                validActions.append(value.name());
            }
            throw new ApiException(ErrorCodes.VALIDATION_FAILED,
                    "invalid captcha action. valid actions: " + validActions);
        }
        CaptchaChallenge challenge = captchaService.createChallenge(action, req.getTenantId(), req.getIdentifier());
        return ResponseEntity.ok(ApiResponse.success(challenge));
    }

    // Helper methods

    /**
     * Build BaseAuthResponse from User object, handling null case (for operations before user exists)
     */
    private BaseAuthResponse buildBaseAuthResponse(User user, String tenantId) {
        if (user == null) {
            return BaseAuthResponse.builder()
                    .tenantId(XssSanitizer.sanitize(tenantId))
                    .userId(null)
                    .username(null)
                    .build();
        }
        return BaseAuthResponse.builder()
                .tenantId(XssSanitizer.sanitize(user.getTenantId()))
                .userId(user.getId())
                .username(XssSanitizer.sanitize(user.getUsername()))
                .build();
    }
}

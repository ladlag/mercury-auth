package com.mercury.auth.controller;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.BaseAuthResponse;
import com.mercury.auth.dto.CaptchaChallenge;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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

    // Password-based authentication endpoints
    
    @PostMapping("/register-password")
    public ResponseEntity<BaseAuthResponse> registerPassword(@Validated @RequestBody AuthRequests.PasswordRegister req) {
        User user = passwordAuthService.registerPassword(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login-password")
    public ResponseEntity<AuthResponse> loginPassword(@Validated @RequestBody AuthRequests.PasswordLogin req) {
        return ResponseEntity.ok(passwordAuthService.loginPassword(req));
    }

    @PostMapping("/change-password")
    public ResponseEntity<BaseAuthResponse> changePassword(@Validated @RequestBody AuthRequests.ChangePassword req) {
        User user = passwordAuthService.changePassword(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<BaseAuthResponse> forgotPassword(@Validated @RequestBody AuthRequests.ForgotPassword req) {
        User user = passwordAuthService.forgotPassword(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/reset-password")
    public ResponseEntity<BaseAuthResponse> resetPassword(@Validated @RequestBody AuthRequests.ResetPassword req) {
        User user = passwordAuthService.resetPassword(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    // Email-based authentication endpoints
    
    @PostMapping("/send-email-code")
    public ResponseEntity<BaseAuthResponse> sendEmailCode(@Validated @RequestBody AuthRequests.SendEmailCode req) {
        User user = emailAuthService.sendEmailCode(req);
        return ResponseEntity.ok(buildBaseAuthResponse(user, req.getTenantId()));
    }

    @PostMapping("/register-email")
    public ResponseEntity<BaseAuthResponse> registerEmail(@Validated @RequestBody AuthRequests.EmailRegister req) {
        User user = emailAuthService.registerEmail(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login-email")
    public ResponseEntity<AuthResponse> loginEmail(@Validated @RequestBody AuthRequests.EmailLogin req) {
        return ResponseEntity.ok(emailAuthService.loginEmail(req));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<BaseAuthResponse> verifyEmail(@Validated @RequestBody AuthRequests.VerifyEmailAfterRegister req) {
        User user = emailAuthService.verifyEmailAfterRegister(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    // Phone-based authentication endpoints
    
    @PostMapping("/send-phone-code")
    public ResponseEntity<BaseAuthResponse> sendPhoneCode(@Validated @RequestBody AuthRequests.SendPhoneCode req) {
        User user = phoneAuthService.sendPhoneCode(req.getTenantId(), req.getPhone(), req.getPurpose());
        return ResponseEntity.ok(buildBaseAuthResponse(user, req.getTenantId()));
    }

    @PostMapping("/register-phone")
    public ResponseEntity<BaseAuthResponse> registerPhone(@Validated @RequestBody AuthRequests.PhoneRegister req) {
        User user = phoneAuthService.registerPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getUsername());
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login-phone")
    public ResponseEntity<AuthResponse> loginPhone(@Validated @RequestBody AuthRequests.PhoneLogin req) {
        return ResponseEntity.ok(phoneAuthService.loginPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getCaptchaId(), req.getCaptcha()));
    }

    @PostMapping("/quick-login-phone")
    public ResponseEntity<AuthResponse> quickLoginPhone(@Validated @RequestBody AuthRequests.PhoneQuickLogin req) {
        return ResponseEntity.ok(phoneAuthService.quickLoginPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getCaptchaId(), req.getCaptcha()));
    }

    // WeChat authentication endpoints
    
    @PostMapping("/wechat-login")
    public ResponseEntity<AuthResponse> wechatLogin(@Validated @RequestBody AuthRequests.WeChatLogin req) {
        return ResponseEntity.ok(weChatAuthService.loginOrRegister(req.getTenantId(), req.getOpenId(), req.getUsername()));
    }

    // Token management endpoints
    
    @PostMapping("/verify-token")
    public ResponseEntity<TokenVerifyResponse> verifyToken(@Validated @RequestBody AuthRequests.TokenVerify req) {
        return ResponseEntity.ok(tokenService.verifyToken(req));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Validated @RequestBody AuthRequests.TokenRefresh req) {
        return ResponseEntity.ok(tokenService.refreshToken(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseAuthResponse> logout(@Validated @RequestBody AuthRequests.TokenLogout req) {
        User user = tokenService.logout(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    // User management endpoints
    
    @PostMapping("/user-status")
    public ResponseEntity<BaseAuthResponse> updateUserStatus(@Validated @RequestBody AuthRequests.UserStatusUpdate req) {
        User user = userService.updateUserStatus(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    // Captcha endpoints
    
    @PostMapping("/captcha")
    public ResponseEntity<CaptchaChallenge> getCaptcha(@Validated @RequestBody AuthRequests.CaptchaRequest req) {
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
        return ResponseEntity.ok(captchaService.createChallenge(action, req.getTenantId(), req.getIdentifier()));
    }

    // Helper methods

    /**
     * Build BaseAuthResponse from User object, handling null case (for operations before user exists)
     */
    private BaseAuthResponse buildBaseAuthResponse(User user, String tenantId) {
        if (user == null) {
            return BaseAuthResponse.builder()
                    .tenantId(tenantId)
                    .userId(null)
                    .username(null)
                    .build();
        }
        return BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }
}

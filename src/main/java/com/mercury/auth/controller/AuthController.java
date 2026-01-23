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
import com.mercury.auth.service.AuthService;
import com.mercury.auth.service.CaptchaService;
import com.mercury.auth.service.PhoneAuthService;
import com.mercury.auth.service.WeChatAuthService;
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

    private final AuthService authService;
    private final PhoneAuthService phoneAuthService;
    private final WeChatAuthService weChatAuthService;
    private final CaptchaService captchaService;

    @PostMapping("/register-password")
    public ResponseEntity<BaseAuthResponse> registerPassword(@Validated @RequestBody AuthRequests.PasswordRegister req) {
        User user = authService.registerPassword(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login-password")
    public ResponseEntity<AuthResponse> loginPassword(@Validated @RequestBody AuthRequests.PasswordLogin req) {
        return ResponseEntity.ok(authService.loginPassword(req));
    }

    @PostMapping("/send-email-code")
    public ResponseEntity<Void> sendEmailCode(@Validated @RequestBody AuthRequests.SendEmailCode req) {
        authService.sendEmailCode(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register-email")
    public ResponseEntity<BaseAuthResponse> registerEmail(@Validated @RequestBody AuthRequests.EmailRegister req) {
        User user = authService.registerEmail(req);
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/login-email")
    public ResponseEntity<AuthResponse> loginEmail(@Validated @RequestBody AuthRequests.EmailLogin req) {
        return ResponseEntity.ok(authService.loginEmail(req));
    }

    @PostMapping("/send-phone-code")
    public ResponseEntity<Void> sendPhoneCode(@Validated @RequestBody AuthRequests.SendPhoneCode req) {
        phoneAuthService.sendPhoneCode(req.getTenantId(), req.getPhone(), req.getPurpose());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login-phone")
    public ResponseEntity<AuthResponse> loginPhone(@Validated @RequestBody AuthRequests.PhoneLogin req) {
        return ResponseEntity.ok(phoneAuthService.loginPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getCaptchaId(), req.getCaptcha()));
    }

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

    @PostMapping("/register-phone")
    public ResponseEntity<BaseAuthResponse> registerPhone(@Validated @RequestBody AuthRequests.PhoneRegister req) {
        User user = phoneAuthService.registerPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getUsername());
        return ResponseEntity.ok(BaseAuthResponse.builder()
                .tenantId(user.getTenantId())
                .userId(user.getId())
                .username(user.getUsername())
                .build());
    }

    @PostMapping("/wechat-login")
    public ResponseEntity<AuthResponse> wechatLogin(@Validated @RequestBody AuthRequests.WeChatLogin req) {
        return ResponseEntity.ok(weChatAuthService.loginOrRegister(req.getTenantId(), req.getOpenId(), req.getUsername()));
    }

    @PostMapping("/verify-token")
    public ResponseEntity<TokenVerifyResponse> verifyToken(@Validated @RequestBody AuthRequests.TokenVerify req) {
        return ResponseEntity.ok(authService.verifyToken(req));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@Validated @RequestBody AuthRequests.TokenRefresh req) {
        return ResponseEntity.ok(authService.refreshToken(req));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Validated @RequestBody AuthRequests.TokenLogout req) {
        authService.logout(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user-status")
    public ResponseEntity<Void> updateUserStatus(@Validated @RequestBody AuthRequests.UserStatusUpdate req) {
        authService.updateUserStatus(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Validated @RequestBody AuthRequests.ChangePassword req) {
        authService.changePassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Validated @RequestBody AuthRequests.ForgotPassword req) {
        authService.forgotPassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Validated @RequestBody AuthRequests.ResetPassword req) {
        authService.resetPassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Validated @RequestBody AuthRequests.VerifyEmailAfterRegister req) {
        authService.verifyEmailAfterRegister(req);
        return ResponseEntity.ok().build();
    }
}

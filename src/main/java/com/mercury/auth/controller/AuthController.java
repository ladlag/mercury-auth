package com.mercury.auth.controller;

import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.service.AuthService;
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

    @PostMapping("/register-password")
    public ResponseEntity<Void> registerPassword(@Validated @RequestBody AuthRequests.PasswordRegister req) {
        authService.registerPassword(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login-password")
    public ResponseEntity<AuthResponse> loginPassword(@Validated @RequestBody AuthRequests.PasswordLogin req) {
        return ResponseEntity.ok(authService.loginPassword(req));
    }

    @PostMapping("/send-email-code")
    public ResponseEntity<String> sendEmailCode(@Validated @RequestBody AuthRequests.SendEmailCode req) {
        return ResponseEntity.ok(authService.sendEmailCode(req));
    }

    @PostMapping("/register-email")
    public ResponseEntity<Void> registerEmail(@Validated @RequestBody AuthRequests.EmailRegister req) {
        authService.registerEmail(req);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login-email")
    public ResponseEntity<AuthResponse> loginEmail(@Validated @RequestBody AuthRequests.EmailLogin req) {
        return ResponseEntity.ok(authService.loginEmail(req));
    }

    @PostMapping("/send-phone-code")
    public ResponseEntity<String> sendPhoneCode(@Validated @RequestBody AuthRequests.SendPhoneCode req) {
        return ResponseEntity.ok(phoneAuthService.sendPhoneCode(req.getTenantId(), req.getPhone()));
    }

    @PostMapping("/login-phone")
    public ResponseEntity<AuthResponse> loginPhone(@Validated @RequestBody AuthRequests.PhoneLogin req) {
        return ResponseEntity.ok(phoneAuthService.loginPhone(req.getTenantId(), req.getPhone(), req.getCode()));
    }

    @PostMapping("/register-phone")
    public ResponseEntity<Void> registerPhone(@Validated @RequestBody AuthRequests.PhoneRegister req) {
        phoneAuthService.registerPhone(req.getTenantId(), req.getPhone(), req.getCode(), req.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/wechat-login")
    public ResponseEntity<AuthResponse> wechatLogin(@Validated @RequestBody AuthRequests.WeChatLogin req) {
        return ResponseEntity.ok(weChatAuthService.loginOrRegister(req.getTenantId(), req.getOpenId(), req.getUsername()));
    }
}

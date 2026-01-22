package com.mercury.auth.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AuthRequests {

    public enum VerificationPurpose {
        REGISTER,
        LOGIN
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordRegister extends BaseTenantRequest {
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
        @NotBlank(message = "Confirm password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String confirmPassword;
        @Email(message = "Invalid email format")
        private String email;
        private String phone;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordLogin extends BaseTenantRequest {
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Password is required")
        private String password;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendEmailCode extends BaseTenantRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRegister extends BaseTenantRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        @NotBlank(message = "Verification code is required")
        private String code;
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
        @NotBlank(message = "Confirm password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailLogin extends BaseTenantRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;
        @NotBlank(message = "Verification code is required")
        private String code;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendPhoneCode extends BaseTenantRequest {
        @NotBlank(message = "Phone number is required")
        private String phone;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserStatusUpdate extends BaseTenantRequest {
        @NotBlank(message = "Username is required")
        private String username;
        private boolean enabled;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChangePassword extends BaseTenantRequest {
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Old password is required")
        private String oldPassword;
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        private String newPassword;
        @NotBlank(message = "Confirm password is required")
        @Size(min = 8, message = "Confirm password must be at least 8 characters")
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneLogin extends BaseTenantRequest {
        @NotBlank(message = "Phone number is required")
        private String phone;
        @NotBlank(message = "Verification code is required")
        private String code;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneRegister extends BaseTenantRequest {
        @NotBlank(message = "Phone number is required")
        private String phone;
        @NotBlank(message = "Verification code is required")
        private String code;
        @NotBlank(message = "Username is required")
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CaptchaRequest extends BaseTenantRequest {
        @NotBlank(message = "Action is required")
        private String action;
        @NotBlank(message = "Identifier is required")
        private String identifier;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WeChatLogin extends BaseTenantRequest {
        @NotBlank(message = "OpenId is required")
        private String openId;
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenVerify extends BaseTenantRequest {
        @NotBlank(message = "Token is required")
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenLogout extends BaseTenantRequest {
        @NotBlank(message = "Token is required")
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenRefresh extends BaseTenantRequest {
        @NotBlank(message = "Token is required")
        private String token;
    }
}

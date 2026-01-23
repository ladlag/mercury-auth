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
        @NotBlank(message = "{validation.username.required}")
        private String username;
        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.size}")
        private String password;
        @NotBlank(message = "{validation.confirmPassword.required}")
        @Size(min = 8, message = "{validation.confirmPassword.size}")
        private String confirmPassword;
        @Email(message = "{validation.email.invalid}")
        private String email;
        private String phone;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordLogin extends BaseTenantRequest {
        @NotBlank(message = "{validation.username.required}")
        private String username;
        @NotBlank(message = "{validation.password.required}")
        private String password;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendEmailCode extends BaseTenantRequest {
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        private String email;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRegister extends BaseTenantRequest {
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        private String email;
        @NotBlank(message = "{validation.code.required}")
        private String code;
        @NotBlank(message = "{validation.username.required}")
        private String username;
        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, message = "{validation.password.size}")
        private String password;
        @NotBlank(message = "{validation.confirmPassword.required}")
        @Size(min = 8, message = "{validation.confirmPassword.size}")
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailLogin extends BaseTenantRequest {
        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        private String email;
        @NotBlank(message = "{validation.code.required}")
        private String code;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendPhoneCode extends BaseTenantRequest {
        @NotBlank(message = "{validation.phone.required}")
        private String phone;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserStatusUpdate extends BaseTenantRequest {
        @NotBlank(message = "{validation.username.required}")
        private String username;
        private boolean enabled;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChangePassword extends BaseTenantRequest {
        @NotBlank(message = "{validation.username.required}")
        private String username;
        @NotBlank(message = "{validation.oldPassword.required}")
        private String oldPassword;
        @NotBlank(message = "{validation.newPassword.required}")
        @Size(min = 8, message = "{validation.newPassword.size}")
        private String newPassword;
        @NotBlank(message = "{validation.confirmPassword.required}")
        @Size(min = 8, message = "{validation.confirmPassword.size}")
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneLogin extends BaseTenantRequest {
        @NotBlank(message = "{validation.phone.required}")
        private String phone;
        @NotBlank(message = "{validation.code.required}")
        private String code;
        private String captchaId;
        private String captcha;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneRegister extends BaseTenantRequest {
        @NotBlank(message = "{validation.phone.required}")
        private String phone;
        @NotBlank(message = "{validation.code.required}")
        private String code;
        @NotBlank(message = "{validation.username.required}")
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CaptchaRequest extends BaseTenantRequest {
        @NotBlank(message = "{validation.action.required}")
        private String action;
        @NotBlank(message = "{validation.identifier.required}")
        private String identifier;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WeChatLogin extends BaseTenantRequest {
        @NotBlank(message = "{validation.openId.required}")
        private String openId;
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenVerify extends BaseTenantRequest {
        @NotBlank(message = "{validation.token.required}")
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenLogout extends BaseTenantRequest {
        @NotBlank(message = "{validation.token.required}")
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenRefresh extends BaseTenantRequest {
        @NotBlank(message = "{validation.token.required}")
        private String token;
    }
}

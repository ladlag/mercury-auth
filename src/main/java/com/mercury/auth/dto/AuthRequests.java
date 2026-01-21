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
        @NotBlank
        private String username;
        @Size(min = 8)
        private String password;
        @Size(min = 8)
        private String confirmPassword;
        private String email;
        private String phone;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordLogin extends BaseTenantRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendEmailCode extends BaseTenantRequest {
        @NotBlank
        @Email
        private String email;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRegister extends BaseTenantRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String code;
        @NotBlank
        private String username;
        @Size(min = 8)
        private String password;
        @Size(min = 8)
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailLogin extends BaseTenantRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String code;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendPhoneCode extends BaseTenantRequest {
        @NotBlank
        private String phone;
        private VerificationPurpose purpose;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserStatusUpdate extends BaseTenantRequest {
        @NotBlank
        private String username;
        private boolean enabled;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChangePassword extends BaseTenantRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String oldPassword;
        @Size(min = 8)
        private String newPassword;
        @Size(min = 8)
        private String confirmPassword;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneLogin extends BaseTenantRequest {
        @NotBlank
        private String phone;
        @NotBlank
        private String code;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneRegister extends BaseTenantRequest {
        @NotBlank
        private String phone;
        @NotBlank
        private String code;
        @NotBlank
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WeChatLogin extends BaseTenantRequest {
        @NotBlank
        private String openId;
        private String username;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenVerify extends BaseTenantRequest {
        @NotBlank
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenLogout extends BaseTenantRequest {
        @NotBlank
        private String token;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenRefresh extends BaseTenantRequest {
        @NotBlank
        private String token;
    }
}

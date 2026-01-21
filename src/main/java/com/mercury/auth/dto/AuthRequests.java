package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AuthRequests {

    @Data
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
    public static class PasswordLogin extends BaseTenantRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class SendEmailCode extends BaseTenantRequest {
        @Email
        private String email;
    }

    @Data
    public static class EmailRegister extends BaseTenantRequest {
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
    public static class EmailLogin extends BaseTenantRequest {
        @Email
        private String email;
        @NotBlank
        private String code;
    }
}

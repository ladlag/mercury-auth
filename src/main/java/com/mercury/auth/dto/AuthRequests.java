package com.mercury.auth.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AuthRequests {

    @Data
    public static class PasswordRegister {
        @NotBlank
        private String tenantId;
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
    public static class PasswordLogin {
        @NotBlank
        private String tenantId;
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @Data
    public static class SendEmailCode {
        @NotBlank
        private String tenantId;
        @Email
        private String email;
    }

    @Data
    public static class EmailRegister {
        @NotBlank
        private String tenantId;
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
    public static class EmailLogin {
        @NotBlank
        private String tenantId;
        @Email
        private String email;
        @NotBlank
        private String code;
    }
}

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

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PasswordLogin extends BaseTenantRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        private String captchaId;
        private String captcha;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendEmailCode extends BaseTenantRequest {
        @NotBlank
        @Email
        private String email;
        private VerificationPurpose purpose;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public VerificationPurpose getPurpose() {
            return purpose;
        }

        public void setPurpose(VerificationPurpose purpose) {
            this.purpose = purpose;
        }
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

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailLogin extends BaseTenantRequest {
        @NotBlank
        @Email
        private String email;
        @NotBlank
        private String code;
        private String captchaId;
        private String captcha;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SendPhoneCode extends BaseTenantRequest {
        @NotBlank
        private String phone;
        private VerificationPurpose purpose;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public VerificationPurpose getPurpose() {
            return purpose;
        }

        public void setPurpose(VerificationPurpose purpose) {
            this.purpose = purpose;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserStatusUpdate extends BaseTenantRequest {
        @NotBlank
        private String username;
        private boolean enabled;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
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

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PhoneLogin extends BaseTenantRequest {
        @NotBlank
        private String phone;
        @NotBlank
        private String code;
        private String captchaId;
        private String captcha;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptcha() {
            return captcha;
        }

        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
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

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CaptchaRequest extends BaseTenantRequest {
        @NotBlank
        private String action;
        @NotBlank
        private String identifier;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WeChatLogin extends BaseTenantRequest {
        @NotBlank
        private String openId;
        private String username;

        public String getOpenId() {
            return openId;
        }

        public void setOpenId(String openId) {
            this.openId = openId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenVerify extends BaseTenantRequest {
        @NotBlank
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenLogout extends BaseTenantRequest {
        @NotBlank
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TokenRefresh extends BaseTenantRequest {
        @NotBlank
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}

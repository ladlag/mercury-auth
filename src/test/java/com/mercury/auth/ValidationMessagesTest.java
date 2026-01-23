package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that validation messages from annotations are properly returned
 * instead of generic "invalid" messages.
 * Also tests internationalization support for Chinese and English messages.
 */
public class ValidationMessagesTest {

    private LocalValidatorFactoryBean createValidator(Locale locale) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:ValidationMessages", "classpath:ErrorMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(locale);
        
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        validator.afterPropertiesSet();
        return validator;
    }

    private GlobalExceptionHandler createHandler(Locale locale) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:ValidationMessages", "classpath:ErrorMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(locale);
        return new GlobalExceptionHandler(messageSource);
    }

    @Test
    void passwordRegister_validation_returns_specific_messages() {
        // Setup validator with English locale
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        // Create request with missing required fields
        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId(""); // empty
        request.setUsername(""); // empty
        request.setPassword(""); // empty
        request.setConfirmPassword(""); // empty
        request.setEmail("invalid-email"); // invalid format

        // Validate
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        // Should have validation errors
        assertThat(bindingResult.hasErrors()).isTrue();

        // Process through GlobalExceptionHandler
        GlobalExceptionHandler handler = createHandler(Locale.ENGLISH);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        // Verify response structure
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getCode());
        assertThat(response.getMessage()).isEqualTo("Validation failed");
        assertThat(response.getErrors()).isNotEmpty();

        // Verify that error messages are NOT "invalid" but actual validation messages
        for (ApiError.FieldError error : response.getErrors()) {
            assertThat(error.getMessage())
                    .as("Field '%s' should have a specific message, not 'invalid'", error.getField())
                    .isNotEqualTo("invalid")
                    .isNotBlank();
        }

        // Verify specific expected messages
        assertThat(response.getErrors())
                .extracting(ApiError.FieldError::getField)
                .contains("tenantId", "username", "password", "confirmPassword", "email");

        // Check some specific messages
        ApiError.FieldError tenantIdError = response.getErrors().stream()
                .filter(e -> e.getField().equals("tenantId"))
                .findFirst()
                .orElse(null);
        assertThat(tenantIdError).isNotNull();
        assertThat(tenantIdError.getMessage()).isEqualTo("Tenant ID is required");

        ApiError.FieldError usernameError = response.getErrors().stream()
                .filter(e -> e.getField().equals("username"))
                .findFirst()
                .orElse(null);
        assertThat(usernameError).isNotNull();
        assertThat(usernameError.getMessage()).isEqualTo("Username is required");

        // Password has both @NotBlank and @Size constraints
        // When empty, it may trigger either constraint first
        ApiError.FieldError passwordError = response.getErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage())
                .as("Password validation message should be one of the two English messages")
                .isIn("Password is required", "Password must be at least 8 characters");

        ApiError.FieldError emailError = response.getErrors().stream()
                .filter(e -> e.getField().equals("email"))
                .findFirst()
                .orElse(null);
        assertThat(emailError).isNotNull();
        assertThat(emailError.getMessage()).isEqualTo("Invalid email format");
    }

    @Test
    void emailRegister_validation_returns_specific_messages() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        AuthRequests.EmailRegister request = new AuthRequests.EmailRegister();
        request.setTenantId("tenant1");
        // Leave other fields blank

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "emailRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.ENGLISH);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isNotEmpty();

        // Verify messages are specific, not "invalid"
        for (ApiError.FieldError error : response.getErrors()) {
            assertThat(error.getMessage()).isNotEqualTo("invalid");
        }

        // Check for expected specific messages
        assertThat(response.getErrors())
                .extracting(ApiError.FieldError::getMessage)
                .contains(
                        "Email is required",
                        "Verification code is required",
                        "Username is required",
                        "Password is required",
                        "Confirm password is required"
                );
    }

    @Test
    void passwordRegister_validation_returns_chinese_messages() {
        // Setup validator with Chinese locale
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        // Create request with missing required fields
        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId(""); // empty
        request.setUsername(""); // empty
        request.setPassword(""); // empty
        request.setConfirmPassword(""); // empty
        request.setEmail("invalid-email"); // invalid format

        // Validate
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        // Should have validation errors
        assertThat(bindingResult.hasErrors()).isTrue();

        // Process through GlobalExceptionHandler
        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        // Verify response structure
        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isNotEmpty();

        // Verify that error messages are in Chinese
        for (ApiError.FieldError error : response.getErrors()) {
            assertThat(error.getMessage())
                    .as("Field '%s' should have a Chinese message", error.getField())
                    .isNotEqualTo("invalid")
                    .isNotBlank();
        }

        // Check some specific Chinese messages
        ApiError.FieldError tenantIdError = response.getErrors().stream()
                .filter(e -> e.getField().equals("tenantId"))
                .findFirst()
                .orElse(null);
        assertThat(tenantIdError).isNotNull();
        assertThat(tenantIdError.getMessage()).isEqualTo("租户ID必填");

        ApiError.FieldError usernameError = response.getErrors().stream()
                .filter(e -> e.getField().equals("username"))
                .findFirst()
                .orElse(null);
        assertThat(usernameError).isNotNull();
        assertThat(usernameError.getMessage()).isEqualTo("用户名必填");

        // Password has both @NotBlank and @Size constraints
        // When empty, it may trigger either constraint first
        ApiError.FieldError passwordError = response.getErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage())
                .as("Password validation message should be one of the two Chinese messages")
                .isIn("密码必填", "密码至少需要8个字符");

        ApiError.FieldError emailError = response.getErrors().stream()
                .filter(e -> e.getField().equals("email"))
                .findFirst()
                .orElse(null);
        assertThat(emailError).isNotNull();
        assertThat(emailError.getMessage()).isEqualTo("邮箱格式无效");
    }

    @Test
    void emailRegister_validation_returns_chinese_messages() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.EmailRegister request = new AuthRequests.EmailRegister();
        request.setTenantId("tenant1");
        // Leave other fields blank

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "emailRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getErrors()).isNotEmpty();

        // Verify messages are specific Chinese messages, not "invalid"
        for (ApiError.FieldError error : response.getErrors()) {
            assertThat(error.getMessage()).isNotEqualTo("invalid");
        }

        // Check for expected specific Chinese messages
        assertThat(response.getErrors())
                .extracting(ApiError.FieldError::getMessage)
                .contains(
                        "邮箱必填",
                        "验证码必填",
                        "用户名必填",
                        "密码必填",
                        "确认密码必填"
                );
    }
}


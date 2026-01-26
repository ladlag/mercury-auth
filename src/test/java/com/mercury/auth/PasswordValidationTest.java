package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import com.mercury.auth.service.LocalizationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify password validation requirements:
 * - Minimum 6 characters, maximum 20 characters
 * - Must contain at least one letter, one number, and one symbol
 */
public class PasswordValidationTest {

    private LocalValidatorFactoryBean createValidator(Locale locale) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:ValidationMessages");
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
        LocalizationService localizationService = new LocalizationService(messageSource);
        return new GlobalExceptionHandler(localizationService);
    }

    @Test
    void passwordRegister_tooShort_returnsChineseError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("ab12!"); // 5 chars - too short
        request.setConfirmPassword("ab12!");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getCode());

        ApiError.FieldError passwordError = response.getErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage()).contains("6-20");
    }

    @Test
    void passwordRegister_tooLong_returnsError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("abcdefghij1234567890!"); // 21 chars - too long
        request.setConfirmPassword("abcdefghij1234567890!");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.ENGLISH);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();

        ApiError.FieldError passwordError = response.getErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage()).contains("6-20");
    }

    @Test
    void passwordRegister_missingLetter_returnsFormatError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("123456!@#"); // no letters
        request.setConfirmPassword("123456!@#");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        ApiError.FieldError passwordError = bindingResult.getFieldErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage()).contains("字母");
    }

    @Test
    void passwordRegister_missingNumber_returnsFormatError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("abcdef!@#"); // no numbers
        request.setConfirmPassword("abcdef!@#");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        ApiError.FieldError passwordError = bindingResult.getFieldErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage()).contains("数字");
    }

    @Test
    void passwordRegister_missingSymbol_returnsFormatError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("abcdef123"); // no symbols
        request.setConfirmPassword("abcdef123");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        ApiError.FieldError passwordError = bindingResult.getFieldErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        assertThat(passwordError.getMessage()).contains("符号");
    }

    @Test
    void passwordRegister_validPasswords_pass() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);

        // Test various valid password formats
        String[] validPasswords = {
            "abc123!",      // minimum length (6)
            "Test123!",     // standard
            "P@ssw0rd",     // standard
            "MyP@ss1",      // standard
            "Complex1!Pass",// longer
            "abcdefgh12345678!@" // maximum length (20)
        };
        
        for (String password : validPasswords) {
            AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
            request.setTenantId("tenant1");
            request.setUsername("testuser");
            request.setPassword(password);
            request.setConfirmPassword(password);

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
            validator.validate(request, bindingResult);

            boolean hasPasswordError = bindingResult.getFieldErrors().stream()
                    .anyMatch(e -> e.getField().equals("password"));
            assertThat(hasPasswordError)
                    .as("Password '%s' should be valid", password)
                    .isFalse();
        }
    }

    @Test
    void passwordRegister_invalidPasswords_fail() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);

        // Test various invalid password formats
        String[] invalidPasswords = {
            "ab12!",        // too short (5 chars)
            "abcdefghij1234567890!", // too long (21 chars)
            "abc123",       // no symbol
            "abcdef!",      // no number
            "123456!",      // no letter
            "password",     // no number or symbol
            "12345678",     // no letter or symbol
            "!@#$%^&*"      // no letter or number
        };
        
        for (String password : invalidPasswords) {
            AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
            request.setTenantId("tenant1");
            request.setUsername("testuser");
            request.setPassword(password);
            request.setConfirmPassword(password);

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
            validator.validate(request, bindingResult);

            boolean hasPasswordError = bindingResult.getFieldErrors().stream()
                    .anyMatch(e -> e.getField().equals("password"));
            assertThat(hasPasswordError)
                    .as("Password '%s' should be invalid", password)
                    .isTrue();
        }
    }

    @Test
    void changePassword_validationWorks() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.ChangePassword request = new AuthRequests.ChangePassword();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setOldPassword("oldPass1!");
        request.setNewPassword("weak"); // too short and missing requirements
        request.setConfirmPassword("weak");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "changePassword");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        boolean hasNewPasswordError = bindingResult.getFieldErrors().stream()
                .anyMatch(e -> e.getField().equals("newPassword"));
        assertThat(hasNewPasswordError).isTrue();
    }

    @Test
    void resetPassword_validationWorks() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);

        AuthRequests.ResetPassword request = new AuthRequests.ResetPassword();
        request.setTenantId("tenant1");
        request.setEmail("test@example.com");
        request.setCode("123456");
        request.setNewPassword("Valid1!Pass"); // valid
        request.setConfirmPassword("Valid1!Pass");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "resetPassword");
        validator.validate(request, bindingResult);

        boolean hasNewPasswordError = bindingResult.getFieldErrors().stream()
                .anyMatch(e -> e.getField().equals("newPassword"));
        assertThat(hasNewPasswordError).isFalse();
    }

    @Test
    void emailRegister_passwordValidation() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.EmailRegister request = new AuthRequests.EmailRegister();
        request.setTenantId("tenant1");
        request.setEmail("test@example.com");
        request.setCode("123456");
        request.setUsername("testuser");
        request.setPassword("Test1!"); // valid 6 chars
        request.setConfirmPassword("Test1!");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "emailRegister");
        validator.validate(request, bindingResult);

        boolean hasPasswordError = bindingResult.getFieldErrors().stream()
                .anyMatch(e -> e.getField().equals("password"));
        assertThat(hasPasswordError).isFalse();
    }

    @Test
    void passwordRegister_chineseErrorMessages() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PasswordRegister request = new AuthRequests.PasswordRegister();
        request.setTenantId("tenant1");
        request.setUsername("testuser");
        request.setPassword("abc"); // too short
        request.setConfirmPassword("abc");
        
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "passwordRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();

        ApiError.FieldError passwordError = response.getErrors().stream()
                .filter(e -> e.getField().equals("password"))
                .findFirst()
                .orElse(null);
        assertThat(passwordError).isNotNull();
        // Should contain Chinese characters for the error message
        assertThat(passwordError.getMessage()).matches(".*[\\u4e00-\\u9fa5].*");
    }
}

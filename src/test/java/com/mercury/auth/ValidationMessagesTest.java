package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that validation messages from annotations are properly returned
 * instead of generic "invalid" messages.
 */
public class ValidationMessagesTest {

    @Test
    void passwordRegister_validation_returns_specific_messages() {
        // Setup validator
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

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
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        // Verify response structure
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getCode());
        assertThat(response.getMessage()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getMessage());
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
                .matches("Password (is required|must be at least 8 characters)");

        ApiError.FieldError emailError = response.getErrors().stream()
                .filter(e -> e.getField().equals("email"))
                .findFirst()
                .orElse(null);
        assertThat(emailError).isNotNull();
        assertThat(emailError.getMessage()).isEqualTo("Invalid email format");
    }

    @Test
    void emailRegister_validation_returns_specific_messages() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        AuthRequests.EmailRegister request = new AuthRequests.EmailRegister();
        request.setTenantId("tenant1");
        // Leave other fields blank

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "emailRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
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
}

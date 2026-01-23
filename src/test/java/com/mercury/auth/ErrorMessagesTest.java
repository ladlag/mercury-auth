package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that error code messages support internationalization
 * for both Chinese and English messages.
 */
public class ErrorMessagesTest {

    private GlobalExceptionHandler createHandler(Locale locale) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:ValidationMessages", "classpath:ErrorMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(locale);
        return new GlobalExceptionHandler(messageSource);
    }

    @Test
    void apiException_returns_english_error_messages() {
        // Setup handler with English locale
        GlobalExceptionHandler handler = createHandler(Locale.ENGLISH);
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        // Test BAD_CREDENTIALS error
        ApiException badCredentialsEx = new ApiException(ErrorCodes.BAD_CREDENTIALS, "test");
        ApiError badCredentialsResponse = handler.handleApi(badCredentialsEx).getBody();
        assertThat(badCredentialsResponse).isNotNull();
        assertThat(badCredentialsResponse.getCode()).isEqualTo("400101");
        assertThat(badCredentialsResponse.getMessage()).isEqualTo("Invalid username or password");

        // Test USER_DISABLED error
        ApiException userDisabledEx = new ApiException(ErrorCodes.USER_DISABLED, "test");
        ApiError userDisabledResponse = handler.handleApi(userDisabledEx).getBody();
        assertThat(userDisabledResponse).isNotNull();
        assertThat(userDisabledResponse.getCode()).isEqualTo("400102");
        assertThat(userDisabledResponse.getMessage()).isEqualTo("User is disabled");

        // Test USER_NOT_FOUND error
        ApiException userNotFoundEx = new ApiException(ErrorCodes.USER_NOT_FOUND, "test");
        ApiError userNotFoundResponse = handler.handleApi(userNotFoundEx).getBody();
        assertThat(userNotFoundResponse).isNotNull();
        assertThat(userNotFoundResponse.getCode()).isEqualTo("400301");
        assertThat(userNotFoundResponse.getMessage()).isEqualTo("User not found");

        // Test DUPLICATE_USERNAME error
        ApiException duplicateUsernameEx = new ApiException(ErrorCodes.DUPLICATE_USERNAME, "test");
        ApiError duplicateUsernameResponse = handler.handleApi(duplicateUsernameEx).getBody();
        assertThat(duplicateUsernameResponse).isNotNull();
        assertThat(duplicateUsernameResponse.getCode()).isEqualTo("400302");
        assertThat(duplicateUsernameResponse.getMessage()).isEqualTo("Username already exists");

        // Test TENANT_NOT_FOUND error
        ApiException tenantNotFoundEx = new ApiException(ErrorCodes.TENANT_NOT_FOUND, "test");
        ApiError tenantNotFoundResponse = handler.handleApi(tenantNotFoundEx).getBody();
        assertThat(tenantNotFoundResponse).isNotNull();
        assertThat(tenantNotFoundResponse.getCode()).isEqualTo("400401");
        assertThat(tenantNotFoundResponse.getMessage()).isEqualTo("Tenant not found");

        // Test INVALID_CODE error
        ApiException invalidCodeEx = new ApiException(ErrorCodes.INVALID_CODE, "test");
        ApiError invalidCodeResponse = handler.handleApi(invalidCodeEx).getBody();
        assertThat(invalidCodeResponse).isNotNull();
        assertThat(invalidCodeResponse.getCode()).isEqualTo("400601");
        assertThat(invalidCodeResponse.getMessage()).isEqualTo("Invalid verification code");

        // Test RATE_LIMITED error
        ApiException rateLimitedEx = new ApiException(ErrorCodes.RATE_LIMITED, "test");
        ApiError rateLimitedResponse = handler.handleApi(rateLimitedEx).getBody();
        assertThat(rateLimitedResponse).isNotNull();
        assertThat(rateLimitedResponse.getCode()).isEqualTo("400701");
        assertThat(rateLimitedResponse.getMessage()).isEqualTo("Too many requests");

        // Test VALIDATION_FAILED error
        ApiException validationFailedEx = new ApiException(ErrorCodes.VALIDATION_FAILED, "test");
        ApiError validationFailedResponse = handler.handleApi(validationFailedEx).getBody();
        assertThat(validationFailedResponse).isNotNull();
        assertThat(validationFailedResponse.getCode()).isEqualTo("400801");
        assertThat(validationFailedResponse.getMessage()).isEqualTo("Validation failed");

        // Test INTERNAL_ERROR error
        ApiException internalErrorEx = new ApiException(ErrorCodes.INTERNAL_ERROR, "test");
        ApiError internalErrorResponse = handler.handleApi(internalErrorEx).getBody();
        assertThat(internalErrorResponse).isNotNull();
        assertThat(internalErrorResponse.getCode()).isEqualTo("409901");
        assertThat(internalErrorResponse.getMessage()).isEqualTo("Internal error");
    }

    @Test
    void apiException_returns_chinese_error_messages() {
        // Setup handler with Chinese locale
        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

        // Test BAD_CREDENTIALS error
        ApiException badCredentialsEx = new ApiException(ErrorCodes.BAD_CREDENTIALS, "test");
        ApiError badCredentialsResponse = handler.handleApi(badCredentialsEx).getBody();
        assertThat(badCredentialsResponse).isNotNull();
        assertThat(badCredentialsResponse.getCode()).isEqualTo("400101");
        assertThat(badCredentialsResponse.getMessage()).isEqualTo("用户名或密码无效");

        // Test USER_DISABLED error
        ApiException userDisabledEx = new ApiException(ErrorCodes.USER_DISABLED, "test");
        ApiError userDisabledResponse = handler.handleApi(userDisabledEx).getBody();
        assertThat(userDisabledResponse).isNotNull();
        assertThat(userDisabledResponse.getCode()).isEqualTo("400102");
        assertThat(userDisabledResponse.getMessage()).isEqualTo("用户已被禁用");

        // Test USER_NOT_FOUND error
        ApiException userNotFoundEx = new ApiException(ErrorCodes.USER_NOT_FOUND, "test");
        ApiError userNotFoundResponse = handler.handleApi(userNotFoundEx).getBody();
        assertThat(userNotFoundResponse).isNotNull();
        assertThat(userNotFoundResponse.getCode()).isEqualTo("400301");
        assertThat(userNotFoundResponse.getMessage()).isEqualTo("用户不存在");

        // Test DUPLICATE_USERNAME error
        ApiException duplicateUsernameEx = new ApiException(ErrorCodes.DUPLICATE_USERNAME, "test");
        ApiError duplicateUsernameResponse = handler.handleApi(duplicateUsernameEx).getBody();
        assertThat(duplicateUsernameResponse).isNotNull();
        assertThat(duplicateUsernameResponse.getCode()).isEqualTo("400302");
        assertThat(duplicateUsernameResponse.getMessage()).isEqualTo("用户名已存在");

        // Test TENANT_NOT_FOUND error
        ApiException tenantNotFoundEx = new ApiException(ErrorCodes.TENANT_NOT_FOUND, "test");
        ApiError tenantNotFoundResponse = handler.handleApi(tenantNotFoundEx).getBody();
        assertThat(tenantNotFoundResponse).isNotNull();
        assertThat(tenantNotFoundResponse.getCode()).isEqualTo("400401");
        assertThat(tenantNotFoundResponse.getMessage()).isEqualTo("租户不存在");

        // Test INVALID_CODE error
        ApiException invalidCodeEx = new ApiException(ErrorCodes.INVALID_CODE, "test");
        ApiError invalidCodeResponse = handler.handleApi(invalidCodeEx).getBody();
        assertThat(invalidCodeResponse).isNotNull();
        assertThat(invalidCodeResponse.getCode()).isEqualTo("400601");
        assertThat(invalidCodeResponse.getMessage()).isEqualTo("验证码无效");

        // Test RATE_LIMITED error
        ApiException rateLimitedEx = new ApiException(ErrorCodes.RATE_LIMITED, "test");
        ApiError rateLimitedResponse = handler.handleApi(rateLimitedEx).getBody();
        assertThat(rateLimitedResponse).isNotNull();
        assertThat(rateLimitedResponse.getCode()).isEqualTo("400701");
        assertThat(rateLimitedResponse.getMessage()).isEqualTo("请求过于频繁");

        // Test VALIDATION_FAILED error
        ApiException validationFailedEx = new ApiException(ErrorCodes.VALIDATION_FAILED, "test");
        ApiError validationFailedResponse = handler.handleApi(validationFailedEx).getBody();
        assertThat(validationFailedResponse).isNotNull();
        assertThat(validationFailedResponse.getCode()).isEqualTo("400801");
        assertThat(validationFailedResponse.getMessage()).isEqualTo("参数校验失败");

        // Test INTERNAL_ERROR error
        ApiException internalErrorEx = new ApiException(ErrorCodes.INTERNAL_ERROR, "test");
        ApiError internalErrorResponse = handler.handleApi(internalErrorEx).getBody();
        assertThat(internalErrorResponse).isNotNull();
        assertThat(internalErrorResponse.getCode()).isEqualTo("409901");
        assertThat(internalErrorResponse.getMessage()).isEqualTo("系统内部错误");
    }

    @Test
    void exception_handler_returns_localized_internal_error() {
        // Test with English locale
        GlobalExceptionHandler englishHandler = createHandler(Locale.ENGLISH);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        
        Exception genericEx = new RuntimeException("test error");
        ApiError englishResponse = englishHandler.handleOther(genericEx).getBody();
        assertThat(englishResponse).isNotNull();
        assertThat(englishResponse.getCode()).isEqualTo("409901");
        assertThat(englishResponse.getMessage()).isEqualTo("Internal error");

        // Test with Chinese locale
        GlobalExceptionHandler chineseHandler = createHandler(Locale.SIMPLIFIED_CHINESE);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        
        ApiError chineseResponse = chineseHandler.handleOther(genericEx).getBody();
        assertThat(chineseResponse).isNotNull();
        assertThat(chineseResponse.getCode()).isEqualTo("409901");
        assertThat(chineseResponse.getMessage()).isEqualTo("系统内部错误");
    }

    @Test
    void all_error_codes_have_translations() {
        // Verify all error codes have corresponding message keys
        for (ErrorCodes errorCode : ErrorCodes.values()) {
            assertThat(errorCode.getMessageKey())
                    .as("Error code %s should have a message key", errorCode.name())
                    .startsWith("error.")
                    .isNotBlank();
            
            // Verify the message key follows the pattern error.{code}
            String expectedKey = "error." + errorCode.getCode();
            assertThat(errorCode.getMessageKey())
                    .as("Error code %s should have message key %s", errorCode.name(), expectedKey)
                    .isEqualTo(expectedKey);
        }
    }
}

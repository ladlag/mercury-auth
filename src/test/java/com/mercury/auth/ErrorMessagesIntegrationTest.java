package com.mercury.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify error messages work with Spring Boot context
 */
@SpringBootTest
@ActiveProfiles("test")
public class ErrorMessagesIntegrationTest {

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void testMessageSourceLoadsErrorMessages() {
        // Test English messages
        String englishMessage = messageSource.getMessage("error.400101", null, Locale.ENGLISH);
        assertThat(englishMessage).isEqualTo("Invalid username or password");

        // Test Chinese messages
        String chineseMessage = messageSource.getMessage("error.400101", null, Locale.SIMPLIFIED_CHINESE);
        assertThat(chineseMessage).isEqualTo("用户名或密码无效");
    }

    @Test
    void testGlobalExceptionHandlerWithEnglishLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        
        ApiException exception = new ApiException(ErrorCodes.BAD_CREDENTIALS, "test");
        ResponseEntity<ApiError> response = globalExceptionHandler.handleApi(exception);
        
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("400101");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void testGlobalExceptionHandlerWithChineseLocale() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        
        ApiException exception = new ApiException(ErrorCodes.USER_NOT_FOUND, "test");
        ResponseEntity<ApiError> response = globalExceptionHandler.handleApi(exception);
        
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("400301");
        assertThat(response.getBody().getMessage()).isEqualTo("用户不存在");
    }

    @Test
    void testValidationFailedMessageInBothLanguages() {
        // Test English
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        String englishMsg = messageSource.getMessage("error.400801", null, Locale.ENGLISH);
        assertThat(englishMsg).isEqualTo("Validation failed");
        
        // Test Chinese
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        String chineseMsg = messageSource.getMessage("error.400801", null, Locale.SIMPLIFIED_CHINESE);
        assertThat(chineseMsg).isEqualTo("参数校验失败");
    }

    @Test
    void testAllErrorCodesHaveTranslations() {
        // Test that all error codes have both English and Chinese translations
        for (ErrorCodes errorCode : ErrorCodes.values()) {
            String messageKey = errorCode.getMessageKey();
            
            // English
            String englishMessage = messageSource.getMessage(messageKey, null, Locale.ENGLISH);
            assertThat(englishMessage)
                    .as("Error code %s should have English translation", errorCode.name())
                    .isNotBlank()
                    .doesNotStartWith("error.");
            
            // Chinese
            String chineseMessage = messageSource.getMessage(messageKey, null, Locale.SIMPLIFIED_CHINESE);
            assertThat(chineseMessage)
                    .as("Error code %s should have Chinese translation", errorCode.name())
                    .isNotBlank()
                    .doesNotStartWith("error.");
            
            // Messages should be different (unless there's no translation)
            assertThat(chineseMessage)
                    .as("Error code %s should have different Chinese and English translations", errorCode.name())
                    .isNotEqualTo(englishMessage);
        }
    }
}

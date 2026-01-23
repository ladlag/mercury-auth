package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import com.mercury.auth.service.LocalizationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class GlobalExceptionHandlerTests {

    private GlobalExceptionHandler createHandler() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:ValidationMessages", "classpath:ErrorMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(Locale.ENGLISH);
        LocalizationService localizationService = new LocalizationService(messageSource);
        return new GlobalExceptionHandler(localizationService);
    }

    @Test
    void handleApi_maps_numeric_code() {
        GlobalExceptionHandler handler = createHandler();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        ApiException ex = new ApiException(ErrorCodes.BAD_CREDENTIALS, "bad credentials");

        ResponseEntity<ApiError> response = handler.handleApi(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCodes.BAD_CREDENTIALS.getCode());
        assertThat(body.getMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void handleValidation_returns_structured_errors() {
        GlobalExceptionHandler handler = createHandler();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        BindException ex = new BindException(new Object(), "passwordRegister");
        ex.addError(new FieldError("passwordRegister", "password", "Password must be at least 8 characters"));
        ex.addError(new FieldError("passwordRegister", "confirmPassword", "Confirm password is required"));

        ResponseEntity<ApiError> response = handler.handleBindValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getCode());
        assertThat(body.getMessage()).isEqualTo("Validation failed");
        assertThat(body.getErrors())
                .extracting("field", "message")
                .containsExactlyInAnyOrder(
                        tuple("password", "Password must be at least 8 characters"),
                        tuple("confirmPassword", "Confirm password is required"));
    }

    @Test
    void handleMessageNotReadable_returns_invalid_request_body_error() {
        GlobalExceptionHandler handler = createHandler();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing",
                (Throwable) null);

        ResponseEntity<ApiError> response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCodes.INVALID_REQUEST_BODY.getCode());
        assertThat(body.getMessage()).isEqualTo("Invalid or missing request body");
        assertThat(body.getErrors()).isNull();
    }

    @Test
    void handleMessageNotReadable_returns_chinese_message() {
        GlobalExceptionHandler handler = createHandler();
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "Required request body is missing",
                (Throwable) null);

        ResponseEntity<ApiError> response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCodes.INVALID_REQUEST_BODY.getCode());
        assertThat(body.getMessage()).isEqualTo("请求体无效或缺失");
    }
}

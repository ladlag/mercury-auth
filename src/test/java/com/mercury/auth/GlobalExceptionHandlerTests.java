package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class GlobalExceptionHandlerTests {

    @Test
    void handleValidation_returns_structured_errors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BindException ex = new BindException(new Object(), "passwordRegister");
        ex.addError(new FieldError("passwordRegister", "password", "too short"));
        ex.addError(new FieldError("passwordRegister", "confirmPassword", "too short"));

        ResponseEntity<ApiError> response = handler.handleBindValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
        assertThat(body.getMessage()).isEqualTo("Validation failed");
        assertThat(body.getErrors())
                .extracting("field", "message")
                .containsExactlyInAnyOrder(
                        tuple("password", "too short"),
                        tuple("confirmPassword", "too short"));
    }
}

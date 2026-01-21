package com.mercury.auth.exception;

import com.mercury.auth.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        logger.warn("api error code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(ex.getCode().name(), ex.getCode().name()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        logger.warn("validation error: {}", ex.getMessage());
        return buildValidationResponse(ex.getBindingResult().getAllErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBindValidation(BindException ex) {
        logger.warn("validation error: {}", ex.getMessage());
        return buildValidationResponse(ex.getBindingResult().getAllErrors());
    }

    private ResponseEntity<ApiError> buildValidationResponse(List<ObjectError> objectErrors) {
        List<ApiError.FieldError> errors = objectErrors.stream()
                .map(error -> {
                    if (error instanceof FieldError) {
                        FieldError fieldError = (FieldError) error;
                        logger.warn("validation field={} message={}", fieldError.getField(), fieldError.getDefaultMessage());
                        return new ApiError.FieldError(fieldError.getField(), "invalid");
                    }
                    logger.warn("validation error={}", error.getDefaultMessage());
                    return new ApiError.FieldError("general", "invalid");
                })
                .collect(Collectors.toList());
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCodes.VALIDATION_FAILED.name(), ErrorCodes.VALIDATION_FAILED.name(), errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        logger.error("internal error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ErrorCodes.INTERNAL_ERROR.name(), INTERNAL_ERROR_MESSAGE));
    }
}

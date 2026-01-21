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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        logger.warn("api error code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.badRequest().body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex) {
        logger.warn("validation error: {}", ex.getMessage());
        List<ApiError.FieldError> errors = extractFieldErrors(ex);
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCodes.VALIDATION_FAILED, "Validation failed", errors));
    }

    private List<ApiError.FieldError> extractFieldErrors(Exception ex) {
        List<ObjectError> objectErrors;
        if (ex instanceof MethodArgumentNotValidException) {
            objectErrors = ((MethodArgumentNotValidException) ex).getBindingResult().getAllErrors();
        } else if (ex instanceof BindException) {
            objectErrors = ((BindException) ex).getBindingResult().getAllErrors();
        } else {
            return Collections.emptyList();
        }
        return objectErrors.stream()
                .map(error -> {
                    if (error instanceof FieldError) {
                        FieldError fieldError = (FieldError) error;
                        return new ApiError.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
                    }
                    return new ApiError.FieldError(error.getObjectName(), error.getDefaultMessage());
                })
                .collect(Collectors.toList());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        logger.error("internal error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ErrorCodes.INTERNAL_ERROR, ex.getMessage()));
    }
}

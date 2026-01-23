package com.mercury.auth.exception;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.service.LocalizationService;
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
    private static final String INVALID_FIELD_MESSAGE = "invalid";
    private final LocalizationService localizationService;

    public GlobalExceptionHandler(LocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        logger.warn("api error code={} message={}", ex.getCode(), ex.getMessage());
        String localizedMessage = localizationService.getMessage(ex.getCode().getMessageKey());
        return ResponseEntity.ok().body(new ApiError(ex.getCodeValue(), localizedMessage));
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
                        String message = fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : INVALID_FIELD_MESSAGE;
                        logger.warn("validation field={} message={}", fieldError.getField(), message);
                        return new ApiError.FieldError(fieldError.getField(), message);
                    }
                    String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : INVALID_FIELD_MESSAGE;
                    logger.warn("validation error={}", message);
                    return new ApiError.FieldError("general", message);
                })
                .collect(Collectors.toList());
        String localizedMessage = localizationService.getMessage(ErrorCodes.VALIDATION_FAILED.getMessageKey());
        return ResponseEntity.ok()
                .body(new ApiError(ErrorCodes.VALIDATION_FAILED.getCode(), localizedMessage, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        logger.error("internal error", ex);
        String localizedMessage = localizationService.getMessage(ErrorCodes.INTERNAL_ERROR.getMessageKey());
        return ResponseEntity.ok()
                .body(new ApiError(ErrorCodes.INTERNAL_ERROR.getCode(), localizedMessage));
    }
}

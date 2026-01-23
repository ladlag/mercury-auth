package com.mercury.auth;

import com.mercury.auth.dto.ApiError;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify phone number validation
 */
public class PhoneValidationTest {

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
        return new GlobalExceptionHandler(messageSource);
    }

    @Test
    void phoneRegister_invalidPhone_returnsChineseError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PhoneRegister request = new AuthRequests.PhoneRegister();
        request.setTenantId("tenant1");
        request.setPhone("12345"); // invalid phone
        request.setCode("123456");
        request.setUsername("testuser");

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "phoneRegister");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.SIMPLIFIED_CHINESE);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ErrorCodes.VALIDATION_FAILED.getCode());

        ApiError.FieldError phoneError = response.getErrors().stream()
                .filter(e -> e.getField().equals("phone"))
                .findFirst()
                .orElse(null);
        assertThat(phoneError).isNotNull();
        assertThat(phoneError.getMessage()).isEqualTo("手机号格式无效");
    }

    @Test
    void phoneRegister_validPhone_passes() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        AuthRequests.PhoneRegister request = new AuthRequests.PhoneRegister();
        request.setTenantId("tenant1");
        request.setPhone("13812345678"); // valid phone
        request.setCode("123456");
        request.setUsername("testuser");

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "phoneRegister");
        validator.validate(request, bindingResult);

        // Should not have phone validation errors (might have other errors if we add more validations)
        ApiError.FieldError phoneError = bindingResult.getFieldErrors().stream()
                .filter(e -> e.getField().equals("phone"))
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .findFirst()
                .orElse(null);
        assertThat(phoneError).isNull();
    }

    @Test
    void sendPhoneCode_invalidPhone_returnsEnglishError() {
        LocalValidatorFactoryBean validator = createValidator(Locale.ENGLISH);

        AuthRequests.SendPhoneCode request = new AuthRequests.SendPhoneCode();
        request.setTenantId("tenant1");
        request.setPhone("999"); // invalid phone

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "sendPhoneCode");
        validator.validate(request, bindingResult);

        assertThat(bindingResult.hasErrors()).isTrue();

        GlobalExceptionHandler handler = createHandler(Locale.ENGLISH);
        BindException bindException = new BindException(bindingResult);
        ApiError response = handler.handleBindValidation(bindException).getBody();

        assertThat(response).isNotNull();

        ApiError.FieldError phoneError = response.getErrors().stream()
                .filter(e -> e.getField().equals("phone"))
                .findFirst()
                .orElse(null);
        assertThat(phoneError).isNotNull();
        assertThat(phoneError.getMessage()).isEqualTo("Invalid phone number format");
    }

    @Test
    void phoneLogin_multiplePhoneValidations() {
        LocalValidatorFactoryBean validator = createValidator(Locale.SIMPLIFIED_CHINESE);

        // Test various invalid phone formats
        String[] invalidPhones = {"", "abc", "123", "12345678901", "0123456789", "21234567890"};
        
        for (String phone : invalidPhones) {
            AuthRequests.PhoneLogin request = new AuthRequests.PhoneLogin();
            request.setTenantId("tenant1");
            request.setPhone(phone);
            request.setCode("123456");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "phoneLogin");
            validator.validate(request, bindingResult);

            assertThat(bindingResult.hasErrors())
                    .as("Phone '%s' should be invalid", phone)
                    .isTrue();
        }

        // Test valid phone formats
        String[] validPhones = {"13812345678", "15912345678", "18812345678", "19912345678"};
        
        for (String phone : validPhones) {
            AuthRequests.PhoneLogin request = new AuthRequests.PhoneLogin();
            request.setTenantId("tenant1");
            request.setPhone(phone);
            request.setCode("123456");

            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "phoneLogin");
            validator.validate(request, bindingResult);

            boolean hasPhoneError = bindingResult.getFieldErrors().stream()
                    .anyMatch(e -> e.getField().equals("phone"));
            assertThat(hasPhoneError)
                    .as("Phone '%s' should be valid", phone)
                    .isFalse();
        }
    }
}

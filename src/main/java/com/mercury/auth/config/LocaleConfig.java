package com.mercury.auth.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Arrays;
import java.util.Locale;

/**
 * Configuration for internationalization (i18n) support.
 * Enables Chinese and English validation messages based on request locale.
 */
@Configuration
public class LocaleConfig {

    /**
     * Configure locale resolver to use Accept-Language header
     * Defaults to Chinese (zh_CN) if no header is provided
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        resolver.setSupportedLocales(Arrays.asList(
                Locale.SIMPLIFIED_CHINESE,
                Locale.ENGLISH
        ));
        return resolver;
    }

    /**
     * Configure message source for validation and error messages
     * Supports ValidationMessages.properties (English) and ValidationMessages_zh_CN.properties (Chinese)
     * Supports ErrorMessages.properties (English) and ErrorMessages_zh_CN.properties (Chinese)
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:ValidationMessages", "classpath:ErrorMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }

    /**
     * Configure validator to use the message source
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }
}

package com.mercury.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service to centralize message localization logic.
 * Provides convenient methods to retrieve localized messages without 
 * repeatedly accessing LocaleContextHolder and MessageSource.
 */
@Service
@RequiredArgsConstructor
public class LocalizationService {

    private final MessageSource messageSource;

    /**
     * Get localized message using current locale from context.
     * 
     * @param key Message key
     * @return Localized message
     */
    public String getMessage(String key) {
        return getMessage(key, null);
    }

    /**
     * Get localized message with parameters using current locale from context.
     * 
     * @param key Message key
     * @param args Message arguments for parameterized messages
     * @return Localized message
     */
    public String getMessage(String key, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, locale);
    }

    /**
     * Get localized message with default fallback.
     * 
     * @param key Message key
     * @param args Message arguments
     * @param defaultMessage Default message if key not found
     * @return Localized message or default message
     */
    public String getMessage(String key, Object[] args, String defaultMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, defaultMessage, locale);
    }

    /**
     * Get current locale from context.
     * 
     * @return Current locale
     */
    public Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }
}

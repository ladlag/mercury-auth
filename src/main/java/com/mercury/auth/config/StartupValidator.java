package com.mercury.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Validates required configuration at application startup.
 * Provides clear error messages when critical configuration is missing.
 */
@Component
public class StartupValidator {

    private static final Logger logger = LoggerFactory.getLogger(StartupValidator.class);

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        logger.info("Validating configuration for profile: {}", activeProfile);

        // Validate JWT secret
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            String errorMsg = "CRITICAL: JWT secret is not configured! " +
                    "Set JWT_SECRET environment variable or security.jwt.secret property.";
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (jwtSecret.length() < 32) {
            logger.warn("WARNING: JWT secret is too short ({}). Recommended minimum: 32 characters", jwtSecret.length());
        }

        if ("prod".equals(activeProfile) || "production".equals(activeProfile)) {
            // Additional validation for production
            if (jwtSecret.contains("dev-secret") || jwtSecret.contains("changeme")) {
                String errorMsg = "CRITICAL: Default/development JWT secret detected in production! " +
                        "Set a strong JWT_SECRET environment variable.";
                logger.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            }
        }

        logger.info("Configuration validation passed");
    }
}

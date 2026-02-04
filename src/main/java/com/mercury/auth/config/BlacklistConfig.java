package com.mercury.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for blacklist functionality.
 * Allows independent control of IP and token blacklisting features.
 */
@Configuration
@ConfigurationProperties(prefix = "security.blacklist")
@Data
public class BlacklistConfig {
    
    /**
     * Enable IP blacklist checking globally
     * When disabled, all IP blacklist checks are bypassed
     */
    private boolean ipEnabled = true;
    
    /**
     * Enable token blacklist checking globally
     * When disabled, all token blacklist checks are bypassed
     */
    private boolean tokenEnabled = true;
    
    /**
     * Cache duration for permanent IP blacklists in Redis (in days)
     * Default: 365 days (1 year)
     */
    private int permanentBlacklistCacheDays = 365;
}
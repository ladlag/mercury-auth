package com.mercury.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for password encryption
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.password")
public class PasswordEncryptionProperties {
    
    /**
     * Enable/disable password encryption feature
     * When enabled, passwords can be encrypted with RSA public key before transmission
     * When disabled, passwords are transmitted in plaintext (current behavior)
     */
    private boolean encryptionEnabled = false;
    
    /**
     * RSA key size in bits (1024, 2048, 4096)
     * Default is 2048 for good security/performance balance
     */
    private int keySize = 2048;
}

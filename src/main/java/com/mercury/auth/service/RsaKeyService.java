package com.mercury.auth.service;

import com.mercury.auth.config.PasswordEncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.*;
import java.util.Base64;

/**
 * Service for managing RSA key pairs for password encryption
 */
@Slf4j
@Service
public class RsaKeyService {

    private final PasswordEncryptionProperties properties;
    private KeyPair keyPair;

    public RsaKeyService(PasswordEncryptionProperties properties) {
        this.properties = properties;
    }

    /**
     * Initialize RSA key pair on service startup
     */
    @PostConstruct
    public void init() {
        if (properties.isEncryptionEnabled()) {
            try {
                generateKeyPair();
                log.info("RSA key pair generated successfully with key size: {} bits", properties.getKeySize());
            } catch (NoSuchAlgorithmException e) {
                log.error("Failed to generate RSA key pair", e);
                throw new RuntimeException("Failed to initialize password encryption service", e);
            }
        } else {
            log.info("Password encryption is disabled");
        }
    }

    /**
     * Generate a new RSA key pair
     */
    private void generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(properties.getKeySize());
        this.keyPair = keyPairGenerator.generateKeyPair();
    }

    /**
     * Get the public key in Base64 encoded format
     *
     * @return Base64 encoded public key
     */
    public String getPublicKeyBase64() {
        if (!properties.isEncryptionEnabled() || keyPair == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    /**
     * Get the private key for decryption
     *
     * @return Private key
     */
    public PrivateKey getPrivateKey() {
        if (!properties.isEncryptionEnabled() || keyPair == null) {
            return null;
        }
        return keyPair.getPrivate();
    }

    /**
     * Get the public key
     *
     * @return Public key
     */
    public PublicKey getPublicKey() {
        if (!properties.isEncryptionEnabled() || keyPair == null) {
            return null;
        }
        return keyPair.getPublic();
    }

    /**
     * Check if encryption is enabled
     *
     * @return true if encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return properties.isEncryptionEnabled();
    }
}

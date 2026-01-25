package com.mercury.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Service for encrypting and decrypting passwords using RSA at tenant level
 */
@Slf4j
@Service
public class PasswordEncryptionService {

    private final RsaKeyService rsaKeyService;

    public PasswordEncryptionService(RsaKeyService rsaKeyService) {
        this.rsaKeyService = rsaKeyService;
    }

    /**
     * Decrypt an RSA encrypted password for a tenant
     *
     * @param tenantId          Tenant identifier
     * @param encryptedPassword Base64 encoded encrypted password
     * @return Decrypted password in plaintext
     * @throws Exception if decryption fails
     */
    public String decrypt(String tenantId, String encryptedPassword) throws Exception {
        if (!rsaKeyService.isEncryptionEnabled(tenantId)) {
            throw new IllegalStateException("Password encryption is not enabled for tenant: " + tenantId);
        }

        PrivateKey privateKey = rsaKeyService.getPrivateKey(tenantId);
        if (privateKey == null) {
            throw new IllegalStateException("Private key is not available for tenant: " + tenantId);
        }

        try {
            // Decode Base64 encrypted password
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPassword);

            // Decrypt using RSA
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt password for tenant: {}", tenantId, e);
            throw new Exception("Failed to decrypt password", e);
        }
    }

    /**
     * Process password based on tenant configuration
     * If encryption is enabled for the tenant, decrypt the password
     * If encryption is disabled, return password as-is
     *
     * @param tenantId Tenant identifier
     * @param password The password (either plaintext or encrypted based on tenant configuration)
     * @return Plaintext password
     * @throws Exception if decryption fails when encryption is enabled
     */
    public String processPassword(String tenantId, String password) throws Exception {
        // If encryption is not enabled for this tenant, return password as-is
        if (!rsaKeyService.isEncryptionEnabled(tenantId)) {
            return password;
        }

        // If encryption is enabled, decrypt the password
        return decrypt(tenantId, password);
    }
}

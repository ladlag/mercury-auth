package com.mercury.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Service for encrypting and decrypting passwords using RSA
 */
@Slf4j
@Service
public class PasswordEncryptionService {

    private final RsaKeyService rsaKeyService;

    public PasswordEncryptionService(RsaKeyService rsaKeyService) {
        this.rsaKeyService = rsaKeyService;
    }

    /**
     * Decrypt an RSA encrypted password
     *
     * @param encryptedPassword Base64 encoded encrypted password
     * @return Decrypted password in plaintext
     * @throws Exception if decryption fails
     */
    public String decrypt(String encryptedPassword) throws Exception {
        if (!rsaKeyService.isEncryptionEnabled()) {
            throw new IllegalStateException("Password encryption is not enabled");
        }

        PrivateKey privateKey = rsaKeyService.getPrivateKey();
        if (privateKey == null) {
            throw new IllegalStateException("Private key is not available");
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
            log.error("Failed to decrypt password", e);
            throw new Exception("Failed to decrypt password", e);
        }
    }

    /**
     * Process password based on encryption mode
     * If encryption is enabled and password appears to be encrypted, decrypt it
     * Otherwise, return the password as-is
     *
     * @param password    The password (either plaintext or encrypted)
     * @param isEncrypted Flag indicating if the password is encrypted
     * @return Plaintext password
     * @throws Exception if decryption fails
     */
    public String processPassword(String password, Boolean isEncrypted) throws Exception {
        // If encryption is not enabled, always return password as-is
        if (!rsaKeyService.isEncryptionEnabled()) {
            return password;
        }

        // If encryption is enabled but password is not encrypted, return as-is
        if (isEncrypted == null || !isEncrypted) {
            return password;
        }

        // Decrypt the encrypted password
        return decrypt(password);
    }
}

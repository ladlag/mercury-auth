package com.mercury.auth.service;

import com.mercury.auth.entity.Tenant;
import com.mercury.auth.store.TenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Service for managing RSA key pairs for password encryption at tenant level
 */
@Slf4j
@Service
public class RsaKeyService {

    private static final int DEFAULT_KEY_SIZE = 2048;
    private final TenantMapper tenantMapper;

    public RsaKeyService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    /**
     * Get tenant configuration
     */
    private Tenant getTenant(String tenantId) {
        return tenantMapper.selectById(tenantId);
    }

    /**
     * Check if encryption is enabled for a tenant
     *
     * @param tenantId Tenant identifier
     * @return true if encryption is enabled for this tenant
     */
    public boolean isEncryptionEnabled(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        return tenant != null && Boolean.TRUE.equals(tenant.getPasswordEncryptionEnabled());
    }

    /**
     * Get the public key in Base64 encoded format for a tenant
     *
     * @param tenantId Tenant identifier
     * @return Base64 encoded public key, or null if encryption is not enabled
     */
    public String getPublicKeyBase64(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null || !Boolean.TRUE.equals(tenant.getPasswordEncryptionEnabled())) {
            return null;
        }
        return tenant.getRsaPublicKey();
    }

    /**
     * Get the private key for decryption for a tenant
     *
     * @param tenantId Tenant identifier
     * @return Private key, or null if encryption is not enabled
     */
    public PrivateKey getPrivateKey(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null || !Boolean.TRUE.equals(tenant.getPasswordEncryptionEnabled())) {
            return null;
        }

        try {
            String privateKeyBase64 = tenant.getRsaPrivateKey();
            if (privateKeyBase64 == null || privateKeyBase64.isEmpty()) {
                return null;
            }

            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            log.error("Failed to load private key for tenant {}", tenantId, e);
            return null;
        }
    }

    /**
     * Get the public key for a tenant
     *
     * @param tenantId Tenant identifier
     * @return Public key, or null if encryption is not enabled
     */
    public PublicKey getPublicKey(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null || !Boolean.TRUE.equals(tenant.getPasswordEncryptionEnabled())) {
            return null;
        }

        try {
            String publicKeyBase64 = tenant.getRsaPublicKey();
            if (publicKeyBase64 == null || publicKeyBase64.isEmpty()) {
                return null;
            }

            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to load public key for tenant {}", tenantId, e);
            return null;
        }
    }

    /**
     * Generate and store RSA key pair for a tenant
     *
     * @param tenantId Tenant identifier
     * @throws Exception if key generation fails
     */
    public void generateAndStoreKeyPair(String tenantId) throws Exception {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        // Generate key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(DEFAULT_KEY_SIZE);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Encode keys to Base64
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        // Update tenant with keys
        tenant.setRsaPublicKey(publicKeyBase64);
        tenant.setRsaPrivateKey(privateKeyBase64);
        tenant.setPasswordEncryptionEnabled(true);
        tenantMapper.updateById(tenant);

        log.info("Generated and stored RSA key pair for tenant: {}", tenantId);
    }

    /**
     * Enable password encryption for a tenant (generates keys if not present)
     *
     * @param tenantId Tenant identifier
     * @throws Exception if operation fails
     */
    public void enableEncryption(String tenantId) throws Exception {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        // Generate keys if not present
        if (tenant.getRsaPublicKey() == null || tenant.getRsaPrivateKey() == null) {
            generateAndStoreKeyPair(tenantId);
        } else {
            // Just enable encryption
            tenant.setPasswordEncryptionEnabled(true);
            tenantMapper.updateById(tenant);
            log.info("Enabled password encryption for tenant: {}", tenantId);
        }
    }

    /**
     * Disable password encryption for a tenant (keeps keys for potential re-enable)
     *
     * @param tenantId Tenant identifier
     */
    public void disableEncryption(String tenantId) {
        Tenant tenant = getTenant(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant not found: " + tenantId);
        }

        tenant.setPasswordEncryptionEnabled(false);
        tenantMapper.updateById(tenant);
        log.info("Disabled password encryption for tenant: {}", tenantId);
    }
}

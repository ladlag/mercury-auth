package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for public key endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicKeyResponse {
    /**
     * Base64 encoded RSA public key
     */
    private String publicKey;
    
    /**
     * Whether password encryption is enabled
     */
    private boolean encryptionEnabled;
    
    /**
     * Key size in bits (e.g., 2048)
     */
    private int keySize;
}

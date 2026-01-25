package com.mercury.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtService.class);

    @Value("${security.jwt.secret:changeme}")
    private String secret;

    @Value("${security.jwt.ttl-seconds:7200}")
    private long ttlSeconds;

    private SecretKey key;

    @PostConstruct
    public void init() {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        
        // SECURITY WARNING: Check if using default or weak secret
        if ("changeme".equals(secret)) {
            logger.error("========================================");
            logger.error("CRITICAL SECURITY WARNING");
            logger.error("Using default JWT secret 'changeme'!");
            logger.error("This is EXTREMELY INSECURE in production!");
            logger.error("Set JWT_SECRET environment variable with a strong random secret (min 32 bytes)");
            logger.error("========================================");
            // In production, this should throw an exception to prevent startup
        } else if (secretBytes.length < 48) {
            // Recommend 48+ bytes for defense in depth, though 32 bytes is cryptographically sufficient for HS256
            logger.warn("========================================");
            logger.warn("SECURITY NOTICE");
            logger.warn("JWT secret is less than 48 bytes (current: {} bytes)", secretBytes.length);
            logger.warn("While 32 bytes is cryptographically sufficient for HS256,");
            logger.warn("we recommend 48+ bytes for defense in depth and future-proofing");
            logger.warn("Generate with: openssl rand -base64 48");
            logger.warn("========================================");
        }
        
        this.key = Keys.hmacShaKeyFor(secretBytes);
        logger.info("JWT service initialized with secret length: {} bytes, TTL: {} seconds", 
                   secretBytes.length, ttlSeconds);
    }

    public String generate(String tenantId, Long userId, String username) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("tenantId", tenantId);
        claims.put("userId", userId);
        claims.put("username", username);
        
        // Generate unique JTI (JWT ID) for token tracking and blacklisting
        // Format: {tenantId}:{uuid} to prevent collisions across tenants
        // This ensures JTI uniqueness even if multiple token stores exist
        String jti = tenantId + ":" + UUID.randomUUID().toString();
        
        return Jwts.builder()
                .setClaims(claims)
                .setId(jti)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}

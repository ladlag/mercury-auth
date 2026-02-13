package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.entity.TokenBlacklist;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.TokenBlacklistMapper;
import com.mercury.auth.store.UserMapper;
import com.mercury.auth.util.TokenHashUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final TenantService tenantService;
    private final AuthLogService authLogService;
    private final TokenBlacklistMapper tokenBlacklistMapper;
    private final RateLimitService rateLimitService;
    private final TokenCacheService tokenCacheService;
    private final com.mercury.auth.config.BlacklistConfig blacklistConfig;

    /**
     * Verify token using request DTO
     */
    public TokenVerifyResponse verifyToken(AuthRequests.TokenVerify req) {
        return verifyToken(req.getTenantId(), req.getToken());
    }

    /**
     * Refresh token using request DTO
     */
    public AuthResponse refreshToken(AuthRequests.TokenRefresh req) {
        return refreshToken(req.getTenantId(), req.getToken());
    }

    /**
     * Logout (blacklist token) using request DTO
     */
    public User logout(AuthRequests.TokenLogout req) {
        return blacklistToken(req.getTenantId(), req.getToken());
    }

    public TokenVerifyResponse verifyToken(String tenantId, String token) {
        // Hash token once for both blacklist check and cache lookup
        String tokenHash = TokenHashUtil.hashToken(token);
        
        // CRITICAL: Check blacklist BEFORE cache to prevent returning cached responses for blacklisted tokens
        if (isBlacklistedByHash(tokenHash)) {
            logger.warn("verifyToken blacklisted tenant={}", tenantId);
            recordFailure(tenantId, null, AuthAction.VERIFY_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        
        // Check if verification result is cached (performance optimization)
        TokenVerifyResponse cached = tokenCacheService.getCachedVerifyResponse(tokenHash);
        if (cached != null) {
            // Verify tenant match even for cached responses
            if (!tenantId.equals(cached.getTenantId())) {
                logger.warn("verifyToken tenant mismatch cached tenant={} requested={}", cached.getTenantId(), tenantId);
                throw new ApiException(ErrorCodes.TENANT_MISMATCH, "tenant mismatch");
            }
            
            // SECURITY: Re-validate tenant and user status even for cached responses
            // This prevents disabled tenants/users from using cached tokens
            try {
                tenantService.requireEnabled(cached.getTenantId());
                User user = loadActiveUser(cached.getTenantId(), cached.getUserId());
                // If tenant or user is disabled, loadActiveUser throws exception, cache is not used
            } catch (ApiException ex) {
                // Tenant or user is disabled, evict from cache and re-throw
                logger.warn("verifyToken cached response invalid, evicting: tenant={} userId={} error={}", 
                    cached.getTenantId(), cached.getUserId(), ex.getCode());
                tokenCacheService.evictToken(tokenHash);
                throw ex;
            }
            
            // SECURITY: Check token expiration time even for cached responses
            long now = System.currentTimeMillis();
            if (cached.getExpiresAt() != null && cached.getExpiresAt() <= now) {
                logger.warn("verifyToken cached token expired tenant={} userId={}", cached.getTenantId(), cached.getUserId());
                tokenCacheService.evictToken(tokenHash);
                recordFailure(tenantId, cached.getUserId(), AuthAction.VERIFY_TOKEN);
                throw new ApiException(ErrorCodes.INVALID_TOKEN, "token expired");
            }
            
            // Log successful token verification (cache hit) for audit trail
            logger.debug("Token verification cache hit for hash: {}", tokenHash.substring(0, Math.min(10, tokenHash.length())));
            safeRecord(tenantId, cached.getUserId(), AuthAction.VERIFY_TOKEN, true);
            return cached;
        }
        
        Claims claims = parseClaims(token);
        
        // Check JTI blacklist if present
        String jti = claims.getId();
        if (jti != null && isJtiBlacklisted(jti)) {
            logger.warn("verifyToken JTI blacklisted tenant={} jti={}", tenantId, jti);
            recordFailure(tenantId, null, AuthAction.VERIFY_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        
        // Check if all user tokens have been revoked (e.g., after password change)
        if (claims.getIssuedAt() != null && 
            isTokenRevokedForUser(tokenTenant, userId, claims.getIssuedAt().getTime())) {
            logger.warn("verifyToken token revoked for user tenant={} userId={} issuedAt={}", 
                    tokenTenant, userId, claims.getIssuedAt().getTime());
            recordFailure(tenantId, userId, AuthAction.VERIFY_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_REVOKED, "token revoked");
        }
        
        User user = loadActiveUser(tokenTenant, userId);
        safeRecord(tenantId, userId, AuthAction.VERIFY_TOKEN, true);
        
        TokenVerifyResponse response = TokenVerifyResponse.builder()
                .tenantId(tokenTenant)
                .userId(userId)
                .userName(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .expiresAt(claims.getExpiration().getTime())  // Convert Date to timestamp
                .build();
        
        // Cache the verification result to improve performance on subsequent requests
        tokenCacheService.cacheVerifyResponse(tokenHash, response);
        
        return response;
    }

    public User blacklistToken(String tenantId, String token) {
        Claims claims = parseClaims(token);
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        User user = loadActiveUser(tokenTenant, userId);
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            logger.warn("logout token expired tenant={} userId={}", tenantId, userId);
            recordFailure(tenantId, userId, AuthAction.LOGOUT);
            throw new ApiException(ErrorCodes.INVALID_TOKEN, "invalid token");
        }
        
        // Evict from cache immediately
        String tokenHash = TokenHashUtil.hashToken(token);
        tokenCacheService.evictToken(tokenHash);
        // Blacklist by token hash
        redisTemplate.opsForValue().set(buildBlacklistKeyFromHash(tokenHash), tokenTenant, ttl);
        
        // Also blacklist by JTI if present for distributed tracking
        String jti = claims.getId();
        if (jti != null) {
            redisTemplate.opsForValue().set(buildJtiBlacklistKey(jti), tokenTenant, ttl);
        }
        
        TokenBlacklist entry = new TokenBlacklist();
        entry.setTokenHash(tokenHash);
        entry.setTenantId(tokenTenant);
        entry.setExpiresAt(LocalDateTime.now().plusSeconds(ttl.getSeconds()));
        entry.setCreatedAt(LocalDateTime.now());
        try {
            tokenBlacklistMapper.insert(entry);
        } catch (Exception ex) {
            logger.error("token blacklist insert failed tenant={} tokenHash={}: {}", tokenTenant, entry.getTokenHash(), ex.getMessage(), ex);
        }
        safeRecord(tenantId, userId, AuthAction.LOGOUT, true);
        return user;
    }

    public AuthResponse refreshToken(String tenantId, String token) {
        // Apply rate limiting for refresh token endpoint
        rateLimitService.checkIpRateLimit(AuthAction.RATE_LIMIT_REFRESH_TOKEN.name());
        
        if (isBlacklisted(token)) {
            logger.warn("refreshToken blacklisted tenant={}", tenantId);
            recordFailure(tenantId, null, AuthAction.REFRESH_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        Claims claims = parseClaims(token);
        
        // Check JTI blacklist if present
        String jti = claims.getId();
        if (jti != null && isJtiBlacklisted(jti)) {
            logger.warn("refreshToken JTI blacklisted tenant={} jti={}", tenantId, jti);
            recordFailure(tenantId, null, AuthAction.REFRESH_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        User user = loadActiveUser(tokenTenant, userId);
        
        // Apply per-user rate limiting
        rateLimitService.check("rate:RATE_LIMIT_REFRESH_TOKEN:" + tokenTenant + ":" + userId,
                AuthAction.RATE_LIMIT_REFRESH_TOKEN);
        
        String newToken = jwtService.generate(tokenTenant, userId, user.getUsername());
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!ttl.isNegative() && !ttl.isZero()) {
            // Evict old token from cache immediately
            String tokenHash = TokenHashUtil.hashToken(token);
            tokenCacheService.evictToken(tokenHash);
            // Blacklist old token by hash
            redisTemplate.opsForValue().set(buildBlacklistKeyFromHash(tokenHash), tokenTenant, ttl);
            
            // Also blacklist by JTI if present
            if (jti != null) {
                redisTemplate.opsForValue().set(buildJtiBlacklistKey(jti), tokenTenant, ttl);
            }
            
            TokenBlacklist entry = new TokenBlacklist();
            entry.setTokenHash(tokenHash);
            entry.setTenantId(tokenTenant);
            entry.setExpiresAt(LocalDateTime.now().plusSeconds(ttl.getSeconds()));
            entry.setCreatedAt(LocalDateTime.now());
            try {
                tokenBlacklistMapper.insert(entry);
            } catch (Exception ex) {
                logger.error("token blacklist insert failed tenant={} tokenHash={}: {}", tokenTenant, entry.getTokenHash(), ex.getMessage(), ex);
            }
        }
        safeRecord(tenantId, userId, AuthAction.REFRESH_TOKEN, true);
        return new AuthResponse(newToken, jwtService.getTtlSeconds());
    }

    private boolean isBlacklisted(String token) {
        // Skip token blacklist check if disabled
        if (!blacklistConfig.isTokenEnabled()) {
            logger.debug("Token blacklist checking is disabled");
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildBlacklistKey(token)));
    }

    /**
     * Check blacklist status using a precomputed token hash.
     */
    private boolean isBlacklistedByHash(String tokenHash) {
        if (!blacklistConfig.isTokenEnabled()) {
            logger.debug("Token blacklist checking is disabled");
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildBlacklistKeyFromHash(tokenHash)));
    }
    
    /**
     * Public method to check if a token is blacklisted (for JWT filter)
     */
    public boolean isTokenBlacklisted(String token) {
        return isBlacklisted(token);
    }

    /**
     * Public method to check if a token hash is blacklisted (for JWT filter).
     */
    public boolean isTokenHashBlacklisted(String tokenHash) {
        return isBlacklistedByHash(tokenHash);
    }
    
    private boolean isJtiBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildJtiBlacklistKey(jti)));
    }

    private String buildBlacklistKey(String token) {
        return "blacklist:" + TokenHashUtil.hashToken(token);
    }

    /**
     * Build blacklist key from a precomputed token hash.
     */
    private String buildBlacklistKeyFromHash(String tokenHash) {
        return "blacklist:" + tokenHash;
    }
    
    private String buildJtiBlacklistKey(String jti) {
        return "blacklist:jti:" + jti;
    }

    private Claims parseClaims(String token) {
        try {
            return jwtService.parse(token);
        } catch (JwtException ex) {
            throw new ApiException(ErrorCodes.INVALID_TOKEN, "invalid token");
        }
    }

    private String requireTenantMatch(String tenantId, Claims claims) {
        String tokenTenant = String.valueOf(claims.get("tenantId"));
        if (!tenantId.equals(tokenTenant)) {
            throw new ApiException(ErrorCodes.TENANT_MISMATCH, "tenant mismatch");
        }
        return tokenTenant;
    }

    private Long requireUserId(Claims claims) {
        Object userIdClaim = claims.get("userId");
        if (userIdClaim == null) {
            throw new ApiException(ErrorCodes.INVALID_TOKEN, "invalid token");
        }
        return Long.valueOf(String.valueOf(userIdClaim));
    }

    private User loadActiveUser(String tenantId, Long userId) {
        User cached = tokenCacheService.getCachedUser(tenantId, userId);
        if (cached != null) {
            if (Boolean.FALSE.equals(cached.getEnabled())) {
                tokenCacheService.evictUserStatus(tenantId, userId);
                logger.warn("token user disabled tenant={} userId={}", tenantId, userId);
                recordFailure(tenantId, userId, AuthAction.TOKEN_USER_DISABLED);
                throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
            }
            return cached;
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("id", userId);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            logger.warn("token user not found tenant={} userId={}", tenantId, userId);
            recordFailure(tenantId, userId, AuthAction.TOKEN_USER_LOOKUP);
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            logger.warn("token user disabled tenant={} userId={}", tenantId, userId);
            recordFailure(tenantId, userId, AuthAction.TOKEN_USER_DISABLED);
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        tokenCacheService.cacheUser(tenantId, userId, user);
        return user;
    }

    /**
     * Revoke all tokens for a specific user by invalidating the user status cache.
     * This forces all subsequent token verifications to check the database,
     * where the user's lastPasswordChangeAt timestamp can be checked (if implemented).
     * 
     * Note: This is a lightweight approach that doesn't require tracking all issued tokens.
     * For immediate revocation, consider maintaining a user-specific token generation timestamp.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID whose tokens should be revoked
     */
    public void revokeAllUserTokens(String tenantId, Long userId) {
        logger.info("Revoking all tokens for tenant={} userId={}", tenantId, userId);
        
        // Clear all token caches to force re-validation on next request
        // Old tokens will be re-parsed and checked against the revocation timestamp below
        tokenCacheService.evictAllForUserStatusChange(tenantId, userId);
        
        // Store a revocation timestamp in Redis so re-validated tokens issued before this time are rejected
        String revocationKey = "token:revocation:" + tenantId + ":" + userId;
        redisTemplate.opsForValue().set(revocationKey, 
                String.valueOf(System.currentTimeMillis()), 
                Duration.ofHours(24));  // Keep for 24 hours (longer than max token TTL)
        
        logger.info("All tokens revoked for tenant={} userId={}", tenantId, userId);
    }

    /**
     * Check if all user tokens have been revoked (for example, after password change).
     * This should be called during token verification.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID
     * @param tokenIssuedAt Token issued timestamp in milliseconds
     * @return true if the token was issued before the last revocation
     */
    public boolean isTokenRevokedForUser(String tenantId, Long userId, long tokenIssuedAt) {
        String revocationKey = "token:revocation:" + tenantId + ":" + userId;
        String revocationTime = redisTemplate.opsForValue().get(revocationKey);
        
        if (revocationTime != null) {
            try {
                long revokedAt = Long.parseLong(revocationTime);
                // Token is revoked if it was issued before the revocation timestamp
                return tokenIssuedAt < revokedAt;
            } catch (NumberFormatException e) {
                logger.warn("Invalid revocation timestamp for tenant={} userId={}", tenantId, userId);
            }
        }
        
        return false;  // No revocation found, token is valid
    }

    private void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    private void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        try {
            authLogService.record(tenantId, userId, action, success);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

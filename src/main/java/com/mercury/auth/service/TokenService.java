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
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

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
        if (isBlacklisted(token)) {
            logger.warn("verifyToken blacklisted tenant={}", tenantId);
            recordFailure(tenantId, null, AuthAction.VERIFY_TOKEN);
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
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
        User user = loadActiveUser(tokenTenant, userId);
        safeRecord(tenantId, userId, AuthAction.VERIFY_TOKEN, true);
        return TokenVerifyResponse.builder()
                .tenantId(tokenTenant)
                .userId(userId)
                .userName(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
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
        
        // Blacklist by token hash
        redisTemplate.opsForValue().set(buildBlacklistKey(token), tokenTenant, ttl);
        
        // Also blacklist by JTI if present for distributed tracking
        String jti = claims.getId();
        if (jti != null) {
            redisTemplate.opsForValue().set(buildJtiBlacklistKey(jti), tokenTenant, ttl);
        }
        
        TokenBlacklist entry = new TokenBlacklist();
        entry.setTokenHash(hashToken(token));
        entry.setTenantId(tokenTenant);
        entry.setExpiresAt(LocalDateTime.now().plusSeconds(ttl.getSeconds()));
        entry.setCreatedAt(LocalDateTime.now());
        try {
            tokenBlacklistMapper.insert(entry);
        } catch (Exception ex) {
            logger.warn("token blacklist insert failed tenant={} tokenHash={}", tokenTenant, entry.getTokenHash());
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
            // Blacklist old token by hash
            redisTemplate.opsForValue().set(buildBlacklistKey(token), tokenTenant, ttl);
            
            // Also blacklist by JTI if present
            if (jti != null) {
                redisTemplate.opsForValue().set(buildJtiBlacklistKey(jti), tokenTenant, ttl);
            }
            
            TokenBlacklist entry = new TokenBlacklist();
            entry.setTokenHash(hashToken(token));
            entry.setTenantId(tokenTenant);
            entry.setExpiresAt(LocalDateTime.now().plusSeconds(ttl.getSeconds()));
            entry.setCreatedAt(LocalDateTime.now());
            try {
                tokenBlacklistMapper.insert(entry);
            } catch (Exception ex) {
                logger.warn("token blacklist insert failed tenant={} tokenHash={}", tokenTenant, entry.getTokenHash());
            }
        }
        safeRecord(tenantId, userId, AuthAction.REFRESH_TOKEN, true);
        return new AuthResponse(newToken, jwtService.getTtlSeconds());
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildBlacklistKey(token)));
    }
    
    /**
     * Public method to check if a token is blacklisted (for JWT filter)
     */
    public boolean isTokenBlacklisted(String token) {
        return isBlacklisted(token);
    }
    
    private boolean isJtiBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildJtiBlacklistKey(jti)));
    }

    private String buildBlacklistKey(String token) {
        return "blacklist:" + hashToken(token);
    }
    
    private String buildJtiBlacklistKey(String jti) {
        return "blacklist:jti:" + jti;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
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
        return user;
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

package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthLogRequest;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.TokenVerifyResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final TenantService tenantService;
    private final AuthLogService authLogService;

    public TokenVerifyResponse verifyToken(String tenantId, String token) {
        if (isBlacklisted(token)) {
            recordFailure(tenantId, null, "VERIFY_TOKEN");
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        Claims claims = parseClaims(token);
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        User user = loadActiveUser(tokenTenant, userId);
        safeRecord(buildLog(tenantId, userId, "VERIFY_TOKEN", true));
        return new TokenVerifyResponse(tokenTenant, userId, user.getUsername());
    }

    public void blacklistToken(String tenantId, String token) {
        Claims claims = parseClaims(token);
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        loadActiveUser(tokenTenant, userId);
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (ttl.isNegative() || ttl.isZero()) {
            recordFailure(tenantId, userId, "LOGOUT");
            throw new ApiException(ErrorCodes.INVALID_TOKEN, "invalid token");
        }
        redisTemplate.opsForValue().set(buildBlacklistKey(token), tokenTenant, ttl);
        safeRecord(buildLog(tenantId, userId, "LOGOUT", true));
    }

    public AuthResponse refreshToken(String tenantId, String token) {
        if (isBlacklisted(token)) {
            recordFailure(tenantId, null, "REFRESH_TOKEN");
            throw new ApiException(ErrorCodes.TOKEN_BLACKLISTED, "token blacklisted");
        }
        Claims claims = parseClaims(token);
        String tokenTenant = requireTenantMatch(tenantId, claims);
        tenantService.requireEnabled(tokenTenant);
        Long userId = requireUserId(claims);
        User user = loadActiveUser(tokenTenant, userId);
        String newToken = jwtService.generate(tokenTenant, userId, user.getUsername());
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(buildBlacklistKey(token), tokenTenant, ttl);
        }
        safeRecord(buildLog(tenantId, userId, "REFRESH_TOKEN", true));
        return new AuthResponse(newToken, jwtService.getTtlSeconds());
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildBlacklistKey(token)));
    }

    private String buildBlacklistKey(String token) {
        return "blacklist:" + hashToken(token);
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
            recordFailure(tenantId, userId, "TOKEN_USER_LOOKUP");
            throw new ApiException(ErrorCodes.USER_NOT_FOUND, "user not found");
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            recordFailure(tenantId, userId, "TOKEN_USER_DISABLED");
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        return user;
    }

    private AuthLogRequest buildLog(String tenantId, Long userId, String action, boolean success) {
        AuthLogRequest request = new AuthLogRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setAction(action);
        request.setSuccess(success);
        return request;
    }

    private void recordFailure(String tenantId, Long userId, String action) {
        safeRecord(buildLog(tenantId, userId, action, false));
    }

    private void safeRecord(AuthLogRequest request) {
        try {
            authLogService.record(request);
        } catch (Exception ignored) {
            // ignore logging failures
        }
    }
}

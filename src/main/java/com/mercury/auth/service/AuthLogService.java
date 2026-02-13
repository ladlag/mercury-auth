package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.store.AuthLogMapper;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AuthLogService {

    private final AuthLogMapper authLogMapper;
    private static final Logger logger = LoggerFactory.getLogger(AuthLogService.class);

    /**
     * Record an authentication action with IP address extracted from current request context
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     */
    public void record(String tenantId, Long userId, AuthAction action, boolean success) {
        record(tenantId, userId, action, success, IpUtils.getClientIp(), null);
    }

    /**
     * Record an authentication action with IP address and token hash.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @param tokenHash Token hash for correlating with specific tokens (can be null)
     */
    public void record(String tenantId, Long userId, AuthAction action, boolean success, String tokenHash) {
        record(tenantId, userId, action, success, IpUtils.getClientIp(), tokenHash);
    }

    /**
     * Asynchronously record an authentication action with IP address extracted from current request context.
     * This method is non-blocking and suitable for high-throughput scenarios (>500 QPS).
     * The log is written to the database in a background thread.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @return CompletableFuture that completes when the log is written
     */
    @Async("auditLogExecutor")
    public CompletableFuture<Void> recordAsync(String tenantId, Long userId, AuthAction action, boolean success) {
        return recordAsync(tenantId, userId, action, success, IpUtils.getClientIp());
    }

    /**
     * Asynchronously record an authentication action with explicit IP address.
     * This method is non-blocking and suitable for high-throughput scenarios (>500 QPS).
     * The log is written to the database in a background thread.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @param ip Client IP address
     * @return CompletableFuture that completes when the log is written
     */
    @Async("auditLogExecutor")
    public CompletableFuture<Void> recordAsync(String tenantId, Long userId, AuthAction action, boolean success, String ip) {
        try {
            record(tenantId, userId, action, success, ip, null);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to record async audit log action={} tenant={} userId={}", action.name(), tenantId, userId, e);
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    /**
     * Record an authentication action with explicit IP address and optional token hash
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @param ip Client IP address
     * @param tokenHash Token hash for correlating with specific tokens (can be null)
     */
    public void record(String tenantId, Long userId, AuthAction action, boolean success, String ip, String tokenHash) {
        AuthLog log = new AuthLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setAction(action.name());
        log.setSuccess(success);
        log.setIp(ip);
        log.setTokenHash(tokenHash);
        log.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(log);
        logger.info("audit action={} tenant={} userId={} success={} ip={}", action.name(), tenantId, userId, success, ip);
    }

    /**
     * Record an authentication action, swallowing any exceptions.
     * Use this when audit logging should not affect the main operation.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     */
    public void safeRecord(String tenantId, Long userId, AuthAction action, boolean success) {
        safeRecord(tenantId, userId, action, success, null);
    }

    /**
     * Record an authentication action with token hash, swallowing any exceptions.
     * Use this when audit logging should not affect the main operation.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @param tokenHash Token hash for correlating with specific tokens (can be null)
     */
    public void safeRecord(String tenantId, Long userId, AuthAction action, boolean success, String tokenHash) {
        try {
            record(tenantId, userId, action, success, tokenHash);
        } catch (Exception ex) {
            logger.error("Failed to record audit log for tenant={} action={}", tenantId, action, ex);
        }
    }

    /**
     * Record a failed authentication action, swallowing any exceptions.
     * Convenience method equivalent to {@code safeRecord(tenantId, userId, action, false)}.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     */
    public void recordFailure(String tenantId, Long userId, AuthAction action) {
        safeRecord(tenantId, userId, action, false);
    }

    /**
     * Record a failed authentication action with token hash, swallowing any exceptions.
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param tokenHash Token hash for correlating with specific tokens (can be null)
     */
    public void recordFailure(String tenantId, Long userId, AuthAction action, String tokenHash) {
        safeRecord(tenantId, userId, action, false, tokenHash);
    }
}

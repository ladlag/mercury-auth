package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.store.AuthLogMapper;
import com.mercury.auth.util.IpUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
        record(tenantId, userId, action, success, IpUtils.getClientIp());
    }

    /**
     * Record an authentication action with explicit IP address
     *
     * @param tenantId Tenant ID
     * @param userId User ID (can be null for failed attempts)
     * @param action Authentication action type
     * @param success Whether the action was successful
     * @param ip Client IP address
     */
    public void record(String tenantId, Long userId, AuthAction action, boolean success, String ip) {
        AuthLog log = new AuthLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setAction(action.name());
        log.setSuccess(success);
        log.setIp(ip);
        log.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(log);
        logger.info("audit action={} tenant={} userId={} success={} ip={}", action.name(), tenantId, userId, success, ip);
    }
}

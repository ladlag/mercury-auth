package com.mercury.auth.service;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.store.AuthLogMapper;
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

    public void record(String tenantId, Long userId, AuthAction action, boolean success) {
        AuthLog log = new AuthLog();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setAction(action.name());
        log.setSuccess(success);
        log.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(log);
        logger.info("audit action={} tenant={} userId={} success={}", action.name(), tenantId, userId, success);
    }
}

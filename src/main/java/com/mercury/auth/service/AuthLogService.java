package com.mercury.auth.service;

import com.mercury.auth.dto.AuthLogRequest;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.store.AuthLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthLogService {

    private final AuthLogMapper authLogMapper;

    public void record(AuthLogRequest request) {
        AuthLog log = new AuthLog();
        log.setTenantId(request.getTenantId());
        log.setUserId(request.getUserId());
        log.setAction(request.getAction());
        log.setSuccess(request.isSuccess());
        log.setIp(request.getIp());
        log.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(log);
    }
}

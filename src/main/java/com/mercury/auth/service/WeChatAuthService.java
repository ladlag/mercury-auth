package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthLogRequest;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WeChatAuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final TenantService tenantService;
    private final AuthLogService authLogService;

    // Stub for OAuth2 callback handling: openId is trusted input for demo
    public AuthResponse loginOrRegister(String tenantId, String openId, String username) {
        tenantService.requireEnabled(tenantId);
        String effectiveUsername = username;
        if (!StringUtils.hasText(effectiveUsername)) {
            effectiveUsername = "wx_" + openId;
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", effectiveUsername);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            user = new User();
            user.setTenantId(tenantId);
            user.setUsername(effectiveUsername);
            user.setPasswordHash("");
            user.setEnabled(true);
            userMapper.insert(user);
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            recordFailure(tenantId, user.getId(), "LOGIN_WECHAT");
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        safeRecord(buildLog(tenantId, user.getId(), "LOGIN_WECHAT", true));
        return new AuthResponse(token, jwtService.getTtlSeconds());
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

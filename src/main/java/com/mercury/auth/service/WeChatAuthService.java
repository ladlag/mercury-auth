package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WeChatAuthService {

    private static final Logger logger = LoggerFactory.getLogger(WeChatAuthService.class);
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
            logger.warn("wechat login disabled tenant={} username={}", tenantId, effectiveUsername);
            recordFailure(tenantId, user.getId(), AuthAction.LOGIN_WECHAT);
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        safeRecord(tenantId, user.getId(), AuthAction.LOGIN_WECHAT, true);
        return new AuthResponse(token, jwtService.getTtlSeconds());
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

package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.exception.ErrorCodes;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeChatAuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;

    // Stub for OAuth2 callback handling: openId is trusted input for demo
    public AuthResponse loginOrRegister(String tenantId, String openId, String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId).eq("username", username);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            user = new User();
            user.setTenantId(tenantId);
            user.setUsername(username);
            user.setPasswordHash("");
            user.setEnabled(true);
            userMapper.insert(user);
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new ApiException(ErrorCodes.USER_DISABLED, "user disabled");
        }
        String token = jwtService.generate(tenantId, user.getId(), user.getUsername());
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }
}

package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.store.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public void registerPassword(AuthRequests.PasswordRegister req) {
        if (!StringUtils.hasText(req.getPassword()) || !req.getPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("password mismatch");
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("username exists");
        }
        User user = new User();
        user.setTenantId(req.getTenantId());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setEnabled(true);
        userMapper.insert(user);
    }

    public AuthResponse loginPassword(AuthRequests.PasswordLogin req) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", req.getTenantId()).eq("username", req.getUsername());
        User user = userMapper.selectOne(wrapper);
        if (user == null || Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("user not found or disabled");
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("bad credentials");
        }
        String token = jwtService.generate(req.getTenantId(), user.getId(), user.getUsername());
        return new AuthResponse(token, jwtService.getTtlSeconds());
    }
}

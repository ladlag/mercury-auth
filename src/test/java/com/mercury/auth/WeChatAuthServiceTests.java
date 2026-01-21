package com.mercury.auth;

import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.WeChatAuthService;
import com.mercury.auth.store.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class WeChatAuthServiceTests {

    private UserMapper userMapper;
    private JwtService jwtService;
    private TenantService tenantService;
    private AuthLogService authLogService;
    private WeChatAuthService weChatAuthService;

    @BeforeEach
    void setup() {
        userMapper = Mockito.mock(UserMapper.class);
        jwtService = Mockito.mock(JwtService.class);
        tenantService = Mockito.mock(TenantService.class);
        authLogService = Mockito.mock(AuthLogService.class);
        weChatAuthService = new WeChatAuthService(userMapper, jwtService, tenantService, authLogService);
    }

    @Test
    void loginOrRegister_creates_user_if_missing() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(null);
        Mockito.doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(5L);
            return null;
        }).when(userMapper).insert(Mockito.any());
        Mockito.when(jwtService.generate("t1", 5L, "wxu")).thenReturn("tk5");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(40L);
        AuthResponse resp = weChatAuthService.loginOrRegister("t1", "openid", "wxu");
        assertThat(resp.getAccessToken()).isEqualTo("tk5");
    }

    @Test
    void loginOrRegister_defaults_username_when_missing() {
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled("t1");
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(null);
        Mockito.doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(6L);
            return null;
        }).when(userMapper).insert(Mockito.any());
        Mockito.when(jwtService.generate("t1", 6L, "wx_open")).thenReturn("tk6");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(40L);
        AuthResponse resp = weChatAuthService.loginOrRegister("t1", "open", null);
        assertThat(resp.getAccessToken()).isEqualTo("tk6");
    }
}

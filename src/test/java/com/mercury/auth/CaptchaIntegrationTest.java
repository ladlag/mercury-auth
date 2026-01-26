package com.mercury.auth;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.AuthRequests;
import com.mercury.auth.dto.AuthResponse;
import com.mercury.auth.dto.CaptchaChallenge;
import com.mercury.auth.entity.Tenant;
import com.mercury.auth.entity.User;
import com.mercury.auth.exception.ApiException;
import com.mercury.auth.security.JwtService;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.service.PasswordAuthService;
import com.mercury.auth.service.EmailAuthService;
import com.mercury.auth.service.CaptchaService;
import com.mercury.auth.service.PasswordEncryptionService;
import com.mercury.auth.service.RateLimitService;
import com.mercury.auth.service.TenantService;
import com.mercury.auth.service.TokenService;
import com.mercury.auth.service.VerificationService;
import com.mercury.auth.store.UserMapper;
import com.mercury.auth.util.KeyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test to reproduce captcha validation issue
 */
public class CaptchaIntegrationTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CaptchaService captchaService;
    private PasswordAuthService passwordAuthService;
    private EmailAuthService emailAuthService;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private TenantService tenantService;

    @BeforeEach
    void setup() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        
        RateLimitService rateLimitServiceForCaptcha = Mockito.mock(RateLimitService.class);
        captchaService = new CaptchaService(redisTemplate, rateLimitServiceForCaptcha);
        ReflectionTestUtils.setField(captchaService, "ttlMinutes", 5L);
        ReflectionTestUtils.setField(captchaService, "threshold", 3L);
        
        userMapper = Mockito.mock(UserMapper.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        VerificationService verificationService = Mockito.mock(VerificationService.class);
        RateLimitService rateLimitService = Mockito.mock(RateLimitService.class);
        TokenService tokenService = Mockito.mock(TokenService.class);
        tenantService = Mockito.mock(TenantService.class);
        AuthLogService authLogService = Mockito.mock(AuthLogService.class);
        PasswordEncryptionService passwordEncryptionService = Mockito.mock(PasswordEncryptionService.class);
        
        // Mock password encryption service to return input as-is (no encryption)
        try {
            Mockito.doAnswer(invocation -> invocation.getArgument(1))
                    .when(passwordEncryptionService).processPassword(Mockito.any(), Mockito.any());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        passwordAuthService = new PasswordAuthService(userMapper, passwordEncoder, jwtService, 
            verificationService, rateLimitService, tenantService, 
            authLogService, captchaService, passwordEncryptionService);
        emailAuthService = new EmailAuthService(userMapper, passwordEncoder, jwtService, 
            verificationService, rateLimitService, tenantService, 
            authLogService, captchaService, passwordEncryptionService);
    }

    @Test
    void test_captcha_flow_with_correct_identifier() {
        // Simulate 5 failed login attempts to trigger captcha requirement
        String tenantId = "tenant1";
        String username = "testuser";
        String captchaKey = KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, tenantId, username);
        
        // Simulate failure count >= threshold
        Mockito.when(valueOps.get(captchaKey)).thenReturn("5");
        assertThat(captchaService.isRequired(captchaKey)).isTrue();
        
        // User requests captcha with correct identifier
        CaptchaChallenge challenge = captchaService.createChallenge(
            AuthAction.CAPTCHA_LOGIN_PASSWORD, tenantId, username);
        
        assertThat(challenge.getCaptchaId()).isNotNull();
        assertThat(challenge.getQuestion()).isNotNull();
        
        // Extract the answer from the question
        String[] parts = challenge.getQuestion().split(" ");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        int answer = left + right;
        String answerStr = String.valueOf(answer);
        
        // Verify the challenge key was created correctly - now using only captchaId
        String challengeKey = "captcha:challenge:" + challenge.getCaptchaId();
        
        // Mock Redis to return the stored answer
        Mockito.when(valueOps.get(challengeKey)).thenReturn(answerStr);
        
        // Now try to login with captcha
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        
        User user = new User();
        user.setId(1L);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash("hashedPassword");
        user.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(user);
        Mockito.when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        Mockito.when(jwtService.generate(tenantId, 1L, username)).thenReturn("token123");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(3600L);
        
        AuthRequests.PasswordLogin loginReq = new AuthRequests.PasswordLogin();
        loginReq.setTenantId(tenantId);
        loginReq.setUsername(username);
        loginReq.setPassword("password123");
        loginReq.setCaptchaId(challenge.getCaptchaId());
        loginReq.setCaptcha(answerStr);
        
        // This should succeed
        AuthResponse response = passwordAuthService.loginPassword(loginReq);
        assertThat(response.getAccessToken()).isEqualTo("token123");
    }

    @Test
    void test_captcha_flow_with_wrong_identifier() {
        // Simulate 5 failed login attempts to trigger captcha requirement
        String tenantId = "tenant1";
        String username = "testuser";
        String wrongIdentifier = "wronguser";  // User provides wrong identifier when getting captcha
        String captchaKey = KeyUtils.buildCaptchaKey(AuthAction.CAPTCHA_LOGIN_PASSWORD, tenantId, username);
        
        // Simulate failure count >= threshold
        Mockito.when(valueOps.get(captchaKey)).thenReturn("5");
        assertThat(captchaService.isRequired(captchaKey)).isTrue();
        
        // User requests captcha with WRONG identifier
        CaptchaChallenge challenge = captchaService.createChallenge(
            AuthAction.CAPTCHA_LOGIN_PASSWORD, tenantId, wrongIdentifier);  // Wrong!
        
        // Extract the answer from the question
        String[] parts = challenge.getQuestion().split(" ");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        int answer = left + right;
        String answerStr = String.valueOf(answer);
        
        // After the fix, the challenge is stored using only captchaId (no identifier needed)
        String challengeKey = "captcha:challenge:" + challenge.getCaptchaId();
        Mockito.when(valueOps.get(challengeKey)).thenReturn(answerStr);
        
        // Now try to login with captcha
        Mockito.doReturn(new Tenant()).when(tenantService).requireEnabled(tenantId);
        
        User user = new User();
        user.setId(1L);
        user.setTenantId(tenantId);
        user.setUsername(username);  // Correct username
        user.setPasswordHash("hashedPassword");
        user.setEnabled(true);
        Mockito.when(userMapper.selectOne(Mockito.any())).thenReturn(user);
        Mockito.when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        Mockito.when(jwtService.generate(tenantId, 1L, username)).thenReturn("token123");
        Mockito.when(jwtService.getTtlSeconds()).thenReturn(3600L);
        
        AuthRequests.PasswordLogin loginReq = new AuthRequests.PasswordLogin();
        loginReq.setTenantId(tenantId);
        loginReq.setUsername(username);  // Correct username
        loginReq.setPassword("password123");
        loginReq.setCaptchaId(challenge.getCaptchaId());
        loginReq.setCaptcha(answerStr);  // Correct answer
        
        // After the fix, this should now SUCCEED because identifier mismatch no longer matters
        AuthResponse response = passwordAuthService.loginPassword(loginReq);
        assertThat(response.getAccessToken()).isEqualTo("token123");
    }
}

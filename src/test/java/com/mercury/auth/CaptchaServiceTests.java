package com.mercury.auth;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.dto.CaptchaChallenge;
import com.mercury.auth.service.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

public class CaptchaServiceTests {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CaptchaService captchaService;

    @BeforeEach
    void setup() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        captchaService = new CaptchaService(redisTemplate);
        ReflectionTestUtils.setField(captchaService, "ttlMinutes", 5L);
    }

    @Test
    void createChallenge_stores_answer() {
        CaptchaChallenge challenge = captchaService.createChallenge(AuthAction.CAPTCHA_LOGIN_PASSWORD, "t1", "u1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> answerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        Mockito.verify(valueOps).set(keyCaptor.capture(), answerCaptor.capture(), ttlCaptor.capture());
        assertThat(keyCaptor.getValue())
                .isEqualTo("captcha:challenge:" + challenge.getCaptchaId());
        String[] parts = challenge.getQuestion().split(" ");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        assertThat(answerCaptor.getValue()).isEqualTo(String.valueOf(left + right));
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(5));
        assertThat(challenge.getExpiresInSeconds()).isEqualTo(300);
        assertThat(challenge.getCaptchaImage()).isNotEmpty();
    }

    @Test
    void verifyChallenge_consumes_on_match() {
        String key = "captcha:challenge:cid";
        Mockito.when(valueOps.get(key)).thenReturn("4");

        boolean result = captchaService.verifyChallenge("cid", "4");

        assertThat(result).isTrue();
        Mockito.verify(redisTemplate).delete(key);
    }
}

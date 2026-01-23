package com.mercury.auth;

import com.mercury.auth.exception.ApiException;
import com.mercury.auth.service.SmsService;
import com.mercury.auth.service.sms.SmsException;
import com.mercury.auth.service.sms.SmsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SmsServiceTests {

    private SmsService smsService;
    private SmsProvider mockProvider;

    @BeforeEach
    void setup() {
        smsService = new SmsService();
        mockProvider = Mockito.mock(SmsProvider.class);
    }

    @Test
    void init_with_configured_provider() {
        when(mockProvider.isConfigured()).thenReturn(true);
        when(mockProvider.getProviderName()).thenReturn("TestProvider");
        
        ReflectionTestUtils.setField(smsService, "smsProviders", Arrays.asList(mockProvider));
        smsService.init();
        
        assertThat(smsService.isSmsConfigured()).isTrue();
        assertThat(smsService.getActiveProviderName()).isEqualTo("TestProvider");
    }

    @Test
    void init_with_no_providers() {
        ReflectionTestUtils.setField(smsService, "smsProviders", null);
        smsService.init();
        
        assertThat(smsService.isSmsConfigured()).isFalse();
        assertThat(smsService.getActiveProviderName()).isEqualTo("None");
    }

    @Test
    void init_with_unconfigured_provider() {
        when(mockProvider.isConfigured()).thenReturn(false);
        
        ReflectionTestUtils.setField(smsService, "smsProviders", Arrays.asList(mockProvider));
        smsService.init();
        
        assertThat(smsService.isSmsConfigured()).isFalse();
    }

    @Test
    void sendVerificationCode_success() throws SmsException {
        when(mockProvider.isConfigured()).thenReturn(true);
        when(mockProvider.getProviderName()).thenReturn("TestProvider");
        doNothing().when(mockProvider).sendVerificationCode(anyString(), anyString());
        
        ReflectionTestUtils.setField(smsService, "smsProviders", Arrays.asList(mockProvider));
        smsService.init();
        
        smsService.sendVerificationCode("13800138000", "123456");
        
        verify(mockProvider).sendVerificationCode("13800138000", "123456");
    }

    @Test
    void sendVerificationCode_not_configured() {
        ReflectionTestUtils.setField(smsService, "smsProviders", null);
        smsService.init();
        
        assertThatThrownBy(() -> smsService.sendVerificationCode("13800138000", "123456"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SMS service not configured");
    }

    @Test
    void sendVerificationCode_provider_fails() throws SmsException {
        when(mockProvider.isConfigured()).thenReturn(true);
        when(mockProvider.getProviderName()).thenReturn("TestProvider");
        doThrow(new SmsException("Network error")).when(mockProvider).sendVerificationCode(anyString(), anyString());
        
        ReflectionTestUtils.setField(smsService, "smsProviders", Arrays.asList(mockProvider));
        smsService.init();
        
        assertThatThrownBy(() -> smsService.sendVerificationCode("13800138000", "123456"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Failed to send SMS");
    }
}

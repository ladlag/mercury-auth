package com.mercury.auth;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.store.AuthLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthLogServiceTests {

    @Mock
    private AuthLogMapper authLogMapper;

    @InjectMocks
    private AuthLogService authLogService;

    @Test
    public void recordAsync_success_returnsCompletedFuture() throws ExecutionException, InterruptedException {
        // Arrange
        when(authLogMapper.insert(any())).thenReturn(1);

        // Act
        CompletableFuture<Void> result = authLogService.recordAsync("t1", 1L, AuthAction.LOGIN_PASSWORD, true, "192.168.1.1");

        // Assert
        assertNotNull(result);
        assertFalse(result.isCompletedExceptionally());
        result.get(); // Should not throw
        verify(authLogMapper, times(1)).insert(any());
    }

    @Test
    public void recordAsync_failure_returnsFailedFuture() {
        // Arrange
        RuntimeException expectedException = new RuntimeException("Database error");
        doThrow(expectedException).when(authLogMapper).insert(any());

        // Act
        CompletableFuture<Void> result = authLogService.recordAsync("t1", 1L, AuthAction.LOGIN_PASSWORD, true, "192.168.1.1");

        // Assert
        assertNotNull(result);
        assertTrue(result.isCompletedExceptionally());
        
        ExecutionException exception = assertThrows(ExecutionException.class, result::get);
        assertEquals(expectedException, exception.getCause());
        verify(authLogMapper, times(1)).insert(any());
    }

    @Test
    public void record_success_insertsLog() {
        // Arrange
        when(authLogMapper.insert(any())).thenReturn(1);

        // Act
        authLogService.record("t1", 1L, AuthAction.LOGIN_PASSWORD, true, "192.168.1.1", null);

        // Assert
        verify(authLogMapper, times(1)).insert(any());
    }

    @Test
    public void record_withTokenHash_persistsTokenHash() {
        // Arrange
        when(authLogMapper.insert(any())).thenReturn(1);
        String tokenHash = "abc123hash";

        // Act
        authLogService.record("t1", 1L, AuthAction.VERIFY_TOKEN, true, tokenHash);

        // Assert
        ArgumentCaptor<AuthLog> captor = ArgumentCaptor.forClass(AuthLog.class);
        verify(authLogMapper, times(1)).insert(captor.capture());
        AuthLog log = captor.getValue();
        assertEquals("t1", log.getTenantId());
        assertEquals(1L, log.getUserId());
        assertEquals(AuthAction.VERIFY_TOKEN.name(), log.getAction());
        assertTrue(log.getSuccess());
        assertEquals(tokenHash, log.getTokenHash());
    }

    @Test
    public void record_withoutTokenHash_tokenHashIsNull() {
        // Arrange
        when(authLogMapper.insert(any())).thenReturn(1);

        // Act
        authLogService.record("t1", 1L, AuthAction.LOGIN_PASSWORD, true);

        // Assert
        ArgumentCaptor<AuthLog> captor = ArgumentCaptor.forClass(AuthLog.class);
        verify(authLogMapper, times(1)).insert(captor.capture());
        AuthLog log = captor.getValue();
        assertNull(log.getTokenHash());
    }

    @Test
    public void recordFailure_withTokenHash_persistsTokenHash() {
        // Arrange
        when(authLogMapper.insert(any())).thenReturn(1);
        String tokenHash = "failedTokenHash";

        // Act
        authLogService.recordFailure("t1", 1L, AuthAction.LOGOUT, tokenHash);

        // Assert
        ArgumentCaptor<AuthLog> captor = ArgumentCaptor.forClass(AuthLog.class);
        verify(authLogMapper, times(1)).insert(captor.capture());
        AuthLog log = captor.getValue();
        assertFalse(log.getSuccess());
        assertEquals(tokenHash, log.getTokenHash());
    }
}

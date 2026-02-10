package com.mercury.auth;

import com.mercury.auth.dto.AuthAction;
import com.mercury.auth.service.AuthLogService;
import com.mercury.auth.store.AuthLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        authLogService.record("t1", 1L, AuthAction.LOGIN_PASSWORD, true, "192.168.1.1");

        // Assert
        verify(authLogMapper, times(1)).insert(any());
    }
}

package com.mercury.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.service.ScheduledCleanupService;
import com.mercury.auth.store.AuthLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the ScheduledCleanupService to ensure old data is properly cleaned up
 */
@SpringBootTest
@ActiveProfiles("test")
public class ScheduledCleanupServiceTest {

    @Autowired
    private ScheduledCleanupService scheduledCleanupService;

    @Autowired
    private AuthLogMapper authLogMapper;

    @BeforeEach
    public void setup() {
        // Clean up any existing test data
        authLogMapper.delete(new QueryWrapper<>());
    }

    @Test
    public void testCleanupOldAuthLogs() {
        // Create old auth log (10 days old - should be deleted)
        AuthLog oldLog = new AuthLog();
        oldLog.setTenantId("test-tenant");
        oldLog.setUserId(1L);
        oldLog.setAction("LOGIN");
        oldLog.setSuccess(true);
        oldLog.setIp("192.168.1.1");
        oldLog.setCreatedAt(LocalDateTime.now().minusDays(10));
        authLogMapper.insert(oldLog);

        // Create recent auth log (2 days old - should be kept)
        AuthLog recentLog = new AuthLog();
        recentLog.setTenantId("test-tenant");
        recentLog.setUserId(2L);
        recentLog.setAction("LOGIN");
        recentLog.setSuccess(true);
        recentLog.setIp("192.168.1.2");
        recentLog.setCreatedAt(LocalDateTime.now().minusDays(2));
        authLogMapper.insert(recentLog);

        // Verify both logs exist
        assertThat(authLogMapper.selectCount(new QueryWrapper<>())).isEqualTo(2);

        // Run cleanup (default retention is 7 days)
        scheduledCleanupService.cleanupOldAuthLogs();

        // Verify old log was deleted and recent log was kept
        assertThat(authLogMapper.selectCount(new QueryWrapper<>())).isEqualTo(1);
        
        // Verify the remaining log is the recent one
        AuthLog remaining = authLogMapper.selectOne(new QueryWrapper<>());
        assertThat(remaining.getUserId()).isEqualTo(2L);
    }

    @Test
    public void testCleanupNoOldLogs() {
        // Create only recent logs
        AuthLog recentLog1 = new AuthLog();
        recentLog1.setTenantId("test-tenant");
        recentLog1.setUserId(1L);
        recentLog1.setAction("LOGIN");
        recentLog1.setSuccess(true);
        recentLog1.setIp("192.168.1.1");
        recentLog1.setCreatedAt(LocalDateTime.now().minusDays(1));
        authLogMapper.insert(recentLog1);

        AuthLog recentLog2 = new AuthLog();
        recentLog2.setTenantId("test-tenant");
        recentLog2.setUserId(2L);
        recentLog2.setAction("LOGIN");
        recentLog2.setSuccess(true);
        recentLog2.setIp("192.168.1.2");
        recentLog2.setCreatedAt(LocalDateTime.now().minusDays(3));
        authLogMapper.insert(recentLog2);

        // Verify both logs exist
        assertThat(authLogMapper.selectCount(new QueryWrapper<>())).isEqualTo(2);

        // Run cleanup
        scheduledCleanupService.cleanupOldAuthLogs();

        // Verify no logs were deleted
        assertThat(authLogMapper.selectCount(new QueryWrapper<>())).isEqualTo(2);
    }
}

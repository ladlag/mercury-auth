package com.mercury.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mercury.auth.entity.AuthLog;
import com.mercury.auth.store.AuthLogMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Scheduled service to cleanup old data and prevent memory/database growth.
 * Addresses OOM issues caused by unbounded data accumulation.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "security.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledCleanupService.class);
    
    private final AuthLogMapper authLogMapper;
    private final BlacklistService blacklistService;
    
    @Value("${security.audit.retention-days:7}")
    private int auditRetentionDays;
    
    /**
     * Clean up old auth logs to prevent unbounded database growth.
     * Runs daily at 2 AM to minimize impact on production traffic.
     * 
     * This is critical for preventing OOM in pods with limited memory (2C4G).
     * Without cleanup, auth_logs table grows indefinitely causing:
     * - Database connection pool exhaustion
     * - Memory pressure from large result sets
     * - Disk space exhaustion
     */
    @Scheduled(cron = "${security.cleanup.auth-logs-cron:0 0 2 * * ?}")
    public void cleanupOldAuthLogs() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(auditRetentionDays);
            
            QueryWrapper<AuthLog> wrapper = new QueryWrapper<>();
            wrapper.lt("created_at", cutoffDate);
            
            int deletedCount = authLogMapper.delete(wrapper);
            
            if (deletedCount > 0) {
                logger.info("Cleaned up {} old auth log entries (older than {} days)", 
                           deletedCount, auditRetentionDays);
            } else {
                logger.debug("No old auth log entries to clean up");
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup old auth logs", e);
        }
    }
    
    /**
     * Clean up expired IP blacklist entries from database.
     * Runs daily at 3 AM to minimize impact on production traffic.
     * 
     * This prevents unbounded growth of the ip_blacklist table and ensures
     * expired entries don't consume memory during queries.
     */
    @Scheduled(cron = "${security.cleanup.ip-blacklist-cron:0 0 3 * * ?}")
    public void cleanupExpiredIpBlacklist() {
        try {
            int deletedCount = blacklistService.cleanupExpiredIpBlacklist();
            
            if (deletedCount > 0) {
                logger.info("Cleaned up {} expired IP blacklist entries", deletedCount);
            } else {
                logger.debug("No expired IP blacklist entries to clean up");
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup expired IP blacklist entries", e);
        }
    }
}

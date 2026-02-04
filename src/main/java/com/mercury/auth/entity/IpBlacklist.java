package com.mercury.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ip_blacklist")
public class IpBlacklist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ipAddress;
    private String tenantId;  // Optional: null means global blacklist
    private String reason;
    private LocalDateTime expiresAt;  // null means permanent blacklist
    private LocalDateTime createdAt;
    private String createdBy;  // admin username or system
}

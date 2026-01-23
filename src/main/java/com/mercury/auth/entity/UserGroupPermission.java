package com.mercury.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_group_permissions")
public class UserGroupPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long permissionId;
    private LocalDateTime createdAt;
}

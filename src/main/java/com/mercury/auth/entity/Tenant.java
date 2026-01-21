package com.mercury.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tenants")
public class Tenant {
    @TableId
    private String tenantId;
    private String name;
    private Boolean enabled;
}

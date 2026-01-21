package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}

package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.AuthLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLogMapper extends BaseMapper<AuthLog> {
}

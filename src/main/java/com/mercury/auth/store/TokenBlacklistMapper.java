package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.TokenBlacklist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TokenBlacklistMapper extends BaseMapper<TokenBlacklist> {
}

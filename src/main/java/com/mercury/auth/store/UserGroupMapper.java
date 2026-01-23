package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.UserGroup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserGroupMapper extends BaseMapper<UserGroup> {
}

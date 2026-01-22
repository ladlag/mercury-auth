package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.UserGroupPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserGroupPermissionMapper extends BaseMapper<UserGroupPermission> {
}

package com.mercury.auth.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mercury.auth.entity.UserGroupMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserGroupMemberMapper extends BaseMapper<UserGroupMember> {
}

package com.example.hello_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hello_demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据库访问层。
 * 继承 BaseMapper 后，MyBatis-Plus 会提供常用 CRUD 方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

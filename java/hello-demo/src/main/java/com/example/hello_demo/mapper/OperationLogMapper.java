package com.example.hello_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hello_demo.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志数据库访问层。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}

package com.example.hello_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hello_demo.entity.AiPendingAction;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 待确认动作数据库访问层。
 */
@Mapper
public interface AiPendingActionMapper extends BaseMapper<AiPendingAction> {
}

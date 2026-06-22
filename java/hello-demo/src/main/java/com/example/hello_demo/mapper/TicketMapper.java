package com.example.hello_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hello_demo.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单数据库访问层。
 * 负责对 ticket 表进行增删改查操作。
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}

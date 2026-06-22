package com.example.hello_demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.hello_demo.entity.TicketReply;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单回复数据库访问层。
 * 负责对 ticket_reply 表进行增删查操作。
 */
@Mapper
public interface TicketReplyMapper extends BaseMapper<TicketReply> {
}

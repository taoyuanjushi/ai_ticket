package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.TicketReplyCreateDTO;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.enums.TicketReplyType;
import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.enums.UserRole;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.security.PermissionUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工单回复业务逻辑层。
 * 负责新增回复、查询某个工单下的回复列表，并处理相关业务校验。
 */
@Service
public class TicketReplyService {

    private final TicketReplyMapper ticketReplyMapper;
    private final TicketMapper ticketMapper;
    private final OperationLogService operationLogService;
    private final TicketCacheService ticketCacheService;

    public TicketReplyService(TicketReplyMapper ticketReplyMapper, TicketMapper ticketMapper, OperationLogService operationLogService, TicketCacheService ticketCacheService) {
        this.ticketReplyMapper = ticketReplyMapper;
        this.ticketMapper = ticketMapper;
        this.operationLogService = operationLogService;
        this.ticketCacheService = ticketCacheService;
    }

    public TicketReply createReply(Long ticketId, TicketReplyCreateDTO dto) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        Ticket ticket = getExistingTicket(ticketId);
        if (TicketStatus.CLOSED.name().equals(ticket.getStatus())) {
            throw new BusinessException(400, "已关闭工单不允许继续回复");
        }

        if (UserRole.isUser(currentRole) && !currentUserId.equals(ticket.getUserId())) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }

        TicketReply reply = new TicketReply();
        // ticketId 来自路径参数，避免前端通过请求体伪造或覆盖所属工单。
        reply.setTicketId(ticketId);
        // 回复人必须来自 Token，忽略前端可能传入的 userId。
        reply.setUserId(currentUserId);
        reply.setContent(dto.getContent());
        reply.setReplyType(buildReplyType(currentRole));

        ticketReplyMapper.insert(reply);
        operationLogService.record(
                OperationType.REPLY_TICKET.name(),
                BusinessType.TICKET_REPLY.name(),
                reply.getId(),
                "用户回复了工单 #" + ticketId
        );
        ticketCacheService.evictTicketRelated(ticketId);

        return ticketReplyMapper.selectById(reply.getId());
    }

    public TicketReply createAiReply(Long ticketId, AiReplyCreateDTO dto) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        PermissionUtil.requireStaffOrAdmin();

        Ticket ticket = getExistingTicket(ticketId);
        if (TicketStatus.CLOSED.name().equals(ticket.getStatus())) {
            throw new BusinessException(400, "已关闭工单不允许继续回复");
        }

        TicketReply reply = new TicketReply();
        reply.setTicketId(ticketId);
        reply.setUserId(currentUserId);
        reply.setContent(normalizeAiReplyContent(dto));
        reply.setReplyType(TicketReplyType.AI.name());

        ticketReplyMapper.insert(reply);
        operationLogService.record(
                OperationType.AI_WRITE_CONFIRMED.name(),
                BusinessType.TICKET_REPLY.name(),
                reply.getId(),
                "确认保存 AI 回复建议，工单 #" + ticketId
        );
        operationLogService.record(
                OperationType.AI_REPLY_CREATED.name(),
                BusinessType.TICKET_REPLY.name(),
                reply.getId(),
                "保存 AI 回复建议"
        );
        operationLogService.record(
                OperationType.AI_REPLY_SAVED.name(),
                BusinessType.TICKET_REPLY.name(),
                reply.getId(),
                "用户确认保存 AI 回复，工单ID=" + ticketId
        );
        ticketCacheService.evictTicketRelated(ticketId);

        return ticketReplyMapper.selectById(reply.getId());
    }

    public List<TicketReply> getRepliesByTicketId(Long ticketId) {
        Ticket ticket = getExistingTicket(ticketId);
        checkReplyReadable(ticket);

        LambdaQueryWrapper<TicketReply> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketReply::getTicketId, ticketId);
        wrapper.orderByAsc(TicketReply::getCreatedAt);

        return ticketReplyMapper.selectList(wrapper);
    }

    private Ticket getExistingTicket(Long ticketId) {
        if (ticketId == null) {
            throw new BusinessException(400, "ticketId不能为空");
        }

        Ticket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new BusinessException(404, "目标工单不存在。");
        }

        return ticket;
    }

    private String normalizeAiReplyContent(AiReplyCreateDTO dto) {
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(400, "content不能为空");
        }
        String content = dto.getContent().trim();
        if (content.length() > 2000) {
            throw new BusinessException(400, "content长度不能超过2000");
        }
        return content;
    }

    private void checkReplyReadable(Ticket ticket) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String currentRole = PermissionUtil.requireLoginRole();

        if (UserRole.isUser(currentRole) && !currentUserId.equals(ticket.getUserId())) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
    }

    private String buildReplyType(String role) {
        if (UserRole.isUser(role)) {
            return TicketReplyType.USER.name();
        }
        return TicketReplyType.STAFF.name();
    }
}

package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.hello_demo.dto.AiPendingActionConfirmResponse;
import com.example.hello_demo.dto.AiPendingActionCreateRequest;
import com.example.hello_demo.dto.AiPendingActionResponse;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.entity.AiPendingAction;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.enums.AiPendingActionStatus;
import com.example.hello_demo.enums.AiPendingActionType;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.AiPendingActionMapper;
import com.example.hello_demo.security.PermissionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 待确认动作业务服务。
 * 负责保存、查询、确认、取消 Human-in-the-loop 写操作。
 */
@Service
public class AiPendingActionService {

    private static final Duration EXPIRE_AFTER = Duration.ofMinutes(10);

    private final AiPendingActionMapper aiPendingActionMapper;
    private final TicketService ticketService;
    private final TicketReplyService ticketReplyService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPendingActionService(
            AiPendingActionMapper aiPendingActionMapper,
            TicketService ticketService,
            TicketReplyService ticketReplyService,
            OperationLogService operationLogService) {
        this.aiPendingActionMapper = aiPendingActionMapper;
        this.ticketService = ticketService;
        this.ticketReplyService = ticketReplyService;
        this.operationLogService = operationLogService;
    }

    @Transactional
    public AiPendingActionResponse createPendingAction(AiPendingActionCreateRequest request) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        String conversationId = requireConversationId(request.getConversationId());
        AiPendingActionType actionType = requireActionType(request.getActionType());
        Map<String, Object> payload = requirePayload(request.getPayload());
        rejectSensitivePayload(payload);
        validatePayload(actionType, payload);

        cancelExistingPendingActions(currentUserId, conversationId);

        LocalDateTime now = LocalDateTime.now();
        AiPendingAction action = new AiPendingAction();
        action.setUserId(currentUserId);
        action.setConversationId(conversationId);
        action.setActionType(actionType.name());
        action.setPayloadJson(toJson(payload));
        action.setStatus(AiPendingActionStatus.PENDING.name());
        action.setCreatedAt(now);
        action.setUpdatedAt(now);

        aiPendingActionMapper.insert(action);
        operationLogService.record(
                OperationType.AI_PENDING_ACTION_CREATED.name(),
                BusinessType.AI_PENDING_ACTION.name(),
                action.getId(),
                "创建 AI 待确认动作：" + actionType.name() + "，conversationId=" + safeText(conversationId, 80)
        );

        return toResponse(action);
    }

    public AiPendingActionResponse getCurrentPendingAction(String conversationId) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        AiPendingAction action = findCurrentPendingAction(currentUserId, requireConversationId(conversationId));
        if (action == null) {
            return null;
        }
        if (isExpired(action)) {
            markExpired(action);
            return null;
        }
        return toResponse(action);
    }

    @Transactional
    public AiPendingActionConfirmResponse confirmPendingAction(String conversationId) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        AiPendingAction action = requireCurrentPendingAction(currentUserId, conversationId);
        ensurePendingActionOwner(action, currentUserId);
        if (isExpired(action)) {
            markExpired(action);
            throw new BusinessException(400, "待确认操作已过期，请重新发起请求。");
        }

        LocalDateTime now = LocalDateTime.now();
        AiPendingAction patch = new AiPendingAction();
        patch.setStatus(AiPendingActionStatus.CONFIRMED.name());
        patch.setUpdatedAt(now);
        patch.setConfirmedAt(now);

        int rows = aiPendingActionMapper.update(
                patch,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getId, action.getId())
                        .eq(AiPendingAction::getUserId, currentUserId)
                        .eq(AiPendingAction::getStatus, AiPendingActionStatus.PENDING.name())
        );
        if (rows == 0) {
            throw new BusinessException(400, "当前没有待确认的操作，请重新发起请求。");
        }

        Map<String, Object> payload = parsePayload(action);
        Object result = executeBusinessAction(action, payload);

        action.setStatus(AiPendingActionStatus.CONFIRMED.name());
        action.setUpdatedAt(now);
        action.setConfirmedAt(now);
        operationLogService.record(
                OperationType.AI_WRITE_CONFIRMED.name(),
                BusinessType.AI_PENDING_ACTION.name(),
                action.getId(),
                buildConfirmedLogContent(action, payload)
        );

        AiPendingActionConfirmResponse response = new AiPendingActionConfirmResponse();
        response.setPendingAction(toResponse(action));
        response.setResult(result);
        return response;
    }

    @Transactional
    public AiPendingActionResponse cancelPendingAction(String conversationId) {
        Long currentUserId = PermissionUtil.requireLoginUserId();
        AiPendingAction action = requireCurrentPendingAction(currentUserId, conversationId);
        ensurePendingActionOwner(action, currentUserId);
        if (isExpired(action)) {
            markExpired(action);
            throw new BusinessException(400, "待确认操作已过期，请重新发起请求。");
        }

        LocalDateTime now = LocalDateTime.now();
        AiPendingAction patch = new AiPendingAction();
        patch.setStatus(AiPendingActionStatus.CANCELLED.name());
        patch.setUpdatedAt(now);
        patch.setCancelledAt(now);

        int rows = aiPendingActionMapper.update(
                patch,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getId, action.getId())
                        .eq(AiPendingAction::getUserId, currentUserId)
                        .eq(AiPendingAction::getStatus, AiPendingActionStatus.PENDING.name())
        );
        if (rows == 0) {
            throw new BusinessException(400, "当前会话没有待取消的操作。");
        }

        action.setStatus(AiPendingActionStatus.CANCELLED.name());
        action.setUpdatedAt(now);
        action.setCancelledAt(now);
        operationLogService.record(
                OperationType.AI_WRITE_CANCELLED.name(),
                BusinessType.AI_PENDING_ACTION.name(),
                action.getId(),
                "取消 AI 写操作：" + action.getActionType()
                        + "，conversationId=" + safeText(action.getConversationId(), 80)
        );

        return toResponse(action);
    }

    private AiPendingAction requireCurrentPendingAction(Long userId, String conversationId) {
        AiPendingAction action = findCurrentPendingAction(userId, requireConversationId(conversationId));
        if (action == null) {
            throw new BusinessException(400, "当前没有待确认的操作，请重新发起请求。");
        }
        return action;
    }

    private void ensurePendingActionOwner(AiPendingAction action, Long currentUserId) {
        if (action.getUserId() == null || !action.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "你没有权限执行该操作。");
        }
    }

    private AiPendingAction findCurrentPendingAction(Long userId, String conversationId) {
        return aiPendingActionMapper.selectOne(
                new LambdaQueryWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getUserId, userId)
                        .eq(AiPendingAction::getConversationId, conversationId)
                        .eq(AiPendingAction::getStatus, AiPendingActionStatus.PENDING.name())
                        .orderByDesc(AiPendingAction::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    private void cancelExistingPendingActions(Long userId, String conversationId) {
        AiPendingAction patch = new AiPendingAction();
        patch.setStatus(AiPendingActionStatus.CANCELLED.name());
        patch.setUpdatedAt(LocalDateTime.now());
        patch.setCancelledAt(LocalDateTime.now());
        aiPendingActionMapper.update(
                patch,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getUserId, userId)
                        .eq(AiPendingAction::getConversationId, conversationId)
                        .eq(AiPendingAction::getStatus, AiPendingActionStatus.PENDING.name())
        );
    }

    private Object executeBusinessAction(AiPendingAction action, Map<String, Object> payload) {
        AiPendingActionType actionType = requireActionType(action.getActionType());
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            TicketCreateDTO dto = new TicketCreateDTO();
            dto.setTitle(requireText(payload, "title"));
            dto.setContent(requireText(payload, "content"));
            dto.setPriority(optionalText(payload, "priority"));
            dto.setCategory(optionalText(payload, "category"));
            return ticketService.createTicket(dto);
        }

        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            Long ticketId = requireLong(payload, "ticketId");
            Ticket before = ticketService.getTicketById(ticketId);
            Ticket after = ticketService.updateTicketStatus(ticketId, requireText(payload, "status"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", after.getId());
            result.put("oldStatus", before.getStatus());
            result.put("newStatus", after.getStatus());
            result.put("ticket", after);
            return result;
        }

        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            Long ticketId = requireLong(payload, "ticketId");
            AiReplyCreateDTO dto = new AiReplyCreateDTO();
            dto.setContent(requireText(payload, "content"));
            TicketReply reply = ticketReplyService.createAiReply(ticketId, dto);
            return reply;
        }

        throw new BusinessException(400, "actionType不支持");
    }

    private String buildConfirmedLogContent(AiPendingAction action, Map<String, Object> payload) {
        StringBuilder content = new StringBuilder(
                "确认执行 AI 写操作：" + action.getActionType()
                        + "，conversationId=" + safeText(action.getConversationId(), 80)
        );
        if (AiPendingActionType.SAVE_AI_REPLY.name().equals(action.getActionType())) {
            appendOptionalLogField(content, "confidence", payload.get("confidence"));
            appendOptionalLogField(content, "reason", payload.get("reason"));
            Object riskFlags = payload.containsKey("riskFlags")
                    ? payload.get("riskFlags")
                    : payload.get("risk_flags");
            appendOptionalLogField(content, "riskFlags", riskFlags);
        }
        return content.toString();
    }

    private void appendOptionalLogField(StringBuilder content, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return;
        }
        content.append("，").append(key).append("=").append(safeText(text, 200));
    }

    private void validatePayload(AiPendingActionType actionType, Map<String, Object> payload) {
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            requireText(payload, "title");
            requireText(payload, "content");
            return;
        }
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            requireLong(payload, "ticketId");
            requireText(payload, "status");
            return;
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            requireLong(payload, "ticketId");
            requireText(payload, "content");
        }
    }

    private void rejectSensitivePayload(Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            String normalizedKey = key == null ? "" : key.trim().toLowerCase();
            if (normalizedKey.contains("token") || normalizedKey.equals("authorization")) {
                throw new BusinessException(400, "pending_action payload 不能保存 token");
            }
        }
    }

    private boolean isExpired(AiPendingAction action) {
        LocalDateTime createdAt = action.getCreatedAt();
        return createdAt != null && createdAt.plus(EXPIRE_AFTER).isBefore(LocalDateTime.now());
    }

    private void markExpired(AiPendingAction action) {
        AiPendingAction patch = new AiPendingAction();
        patch.setStatus(AiPendingActionStatus.EXPIRED.name());
        patch.setUpdatedAt(LocalDateTime.now());
        aiPendingActionMapper.update(
                patch,
                new LambdaUpdateWrapper<AiPendingAction>()
                        .eq(AiPendingAction::getId, action.getId())
                        .eq(AiPendingAction::getStatus, AiPendingActionStatus.PENDING.name())
        );
    }

    private String requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessException(400, "conversationId不能为空");
        }
        return conversationId.trim();
    }

    private AiPendingActionType requireActionType(String actionType) {
        AiPendingActionType type = AiPendingActionType.from(actionType);
        if (type == null) {
            throw new BusinessException(400, "actionType不合法");
        }
        return type;
    }

    private Map<String, Object> requirePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(400, "payload不能为空");
        }
        return payload;
    }

    private String requireText(Map<String, Object> payload, String key) {
        String text = optionalText(payload, key);
        if (text == null) {
            throw new BusinessException(400, key + "不能为空");
        }
        return text;
    }

    private String optionalText(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Long requireLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                throw new BusinessException(400, key + "参数格式不正确");
            }
        }
        throw new BusinessException(400, key + "不能为空");
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "payload格式不正确");
        }
    }

    private Map<String, Object> parsePayload(AiPendingAction action) {
        try {
            return objectMapper.readValue(
                    action.getPayloadJson(),
                    new TypeReference<>() {
                    }
            );
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "pending_action payload 解析失败");
        }
    }

    private AiPendingActionResponse toResponse(AiPendingAction action) {
        AiPendingActionResponse response = new AiPendingActionResponse();
        response.setId(action.getId());
        response.setUserId(action.getUserId());
        response.setConversationId(action.getConversationId());
        response.setActionType(action.getActionType());
        response.setPayload(parsePayload(action));
        response.setStatus(action.getStatus());
        response.setCreatedAt(action.getCreatedAt());
        response.setUpdatedAt(action.getUpdatedAt());
        response.setConfirmedAt(action.getConfirmedAt());
        response.setCancelledAt(action.getCancelledAt());
        return response;
    }

    private String safeText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}

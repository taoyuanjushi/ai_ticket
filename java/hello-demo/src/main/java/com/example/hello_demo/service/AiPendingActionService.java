package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.hello_demo.dto.AiPendingActionConfirmResponse;
import com.example.hello_demo.dto.AiPendingActionCreateRequest;
import com.example.hello_demo.dto.AiPendingConfirmationResponse;
import com.example.hello_demo.dto.AiPendingActionResponse;
import com.example.hello_demo.dto.AiCategoryPendingRequest;
import com.example.hello_demo.dto.AiReplyPendingRequest;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.TicketCategoryUpdateRequest;
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
        try {
            validateActionCreationPermission(actionType, payload);
        } catch (BusinessException e) {
            if (e.getCode() == 403) {
                recordAiPendingAudit(null, conversationId, actionType, payload, "FORBIDDEN", e.getMessage(), null);
            }
            throw e;
        }

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
        recordSpecificPendingCreatedLog(action, actionType, payload);
        recordAiPendingAudit(action, conversationId, actionType, payload, "SUCCESS", "创建 AI 待确认动作", null);

        return toResponse(action);
    }

    @Transactional
    public AiPendingConfirmationResponse createSaveAiReplyPending(
            Long ticketId,
            AiReplyPendingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", requirePositiveId(ticketId, "ticketId"));
        payload.put("content", requireRequestText(request == null ? null : request.getContent(), "content"));
        payload.put("source", "AI_REPLY_SUGGESTION");

        AiPendingActionResponse pendingAction = createPendingAction(
                createPendingRequest(
                        request == null ? null : request.getConversationId(),
                        AiPendingActionType.SAVE_AI_REPLY,
                        payload
                )
        );

        return toPendingConfirmationResponse(
                "请确认是否保存这条 AI 回复。",
                pendingAction
        );
    }

    @Transactional
    public AiPendingConfirmationResponse createApplyCategoryPending(
            Long ticketId,
            AiCategoryPendingRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", requirePositiveId(ticketId, "ticketId"));
        payload.put("category", requireRequestText(request == null ? null : request.getCategory(), "category"));
        if (request != null && request.getConfidence() != null) {
            payload.put("confidence", request.getConfidence());
        }
        String reason = optionalRequestText(request == null ? null : request.getReason());
        if (reason != null) {
            payload.put("reason", reason);
        }

        AiPendingActionResponse pendingAction = createPendingAction(
                createPendingRequest(
                        request == null ? null : request.getConversationId(),
                        AiPendingActionType.APPLY_AI_CATEGORY,
                        payload
                )
        );

        return toPendingConfirmationResponse(
                "请确认是否将该工单分类更新为：" + payload.get("category") + "。",
                pendingAction
        );
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
        Object result;
        try {
            result = executeBusinessAction(action, payload);
        } catch (BusinessException e) {
            recordAiPendingAudit(action, action.getConversationId(), requireActionType(action.getActionType()), payload,
                    e.getCode() == 403 ? "FORBIDDEN" : "FAILED", e.getMessage(), null);
            throw e;
        }

        action.setStatus(AiPendingActionStatus.CONFIRMED.name());
        action.setUpdatedAt(now);
        action.setConfirmedAt(now);
        operationLogService.record(
                OperationType.AI_WRITE_CONFIRMED.name(),
                BusinessType.AI_PENDING_ACTION.name(),
                action.getId(),
                buildConfirmedLogContent(action, payload)
        );
        recordSpecificConfirmedLog(action, payload, result);
        recordAiPendingAudit(action, action.getConversationId(), requireActionType(action.getActionType()), payload,
                "SUCCESS", "确认执行 AI 待确认动作", result);

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
        operationLogService.record(
                OperationType.AI_ACTION_CANCELLED.name(),
                BusinessType.AI_PENDING_ACTION.name(),
                action.getId(),
                "取消 AI 待确认动作：" + action.getActionType()
                        + "，conversationId=" + safeText(action.getConversationId(), 80)
        );
        recordAiPendingAudit(action, action.getConversationId(), requireActionType(action.getActionType()), parsePayload(action),
                "CANCELLED", "取消 AI 待确认动作", null);

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

        if (actionType == AiPendingActionType.APPLY_AI_CATEGORY) {
            Long ticketId = requireLong(payload, "ticketId");
            Ticket before = ticketService.getTicketById(ticketId);
            TicketCategoryUpdateRequest request = new TicketCategoryUpdateRequest();
            request.setCategory(requireText(payload, "category"));
            Ticket after = ticketService.updateTicketCategory(ticketId, request);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", after.getId());
            result.put("oldCategory", before.getCategory());
            result.put("newCategory", after.getCategory());
            result.put("ticket", after);
            return result;
        }

        throw new BusinessException(400, "actionType不支持");
    }

    private String buildConfirmedLogContent(AiPendingAction action, Map<String, Object> payload) {
        StringBuilder content = new StringBuilder(
                "确认执行 AI 写操作：" + action.getActionType()
                        + "，conversationId=" + safeText(action.getConversationId(), 80)
        );
        if (AiPendingActionType.SAVE_AI_REPLY.name().equals(action.getActionType())
                || AiPendingActionType.APPLY_AI_CATEGORY.name().equals(action.getActionType())) {
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
            requireMaxLength(requireText(payload, "content"), "content", 2000);
            return;
        }
        if (actionType == AiPendingActionType.APPLY_AI_CATEGORY) {
            requireLong(payload, "ticketId");
            requireMaxLength(requireText(payload, "category"), "category", 64);
            validateOptionalConfidence(payload.get("confidence"));
        }
    }

    private void validateActionCreationPermission(AiPendingActionType actionType, Map<String, Object> payload) {
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            PermissionUtil.requireStaffOrAdmin();
            ticketService.getTicketById(requireLong(payload, "ticketId"));
            return;
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            PermissionUtil.requireStaffOrAdmin();
            ticketService.getTicketById(requireLong(payload, "ticketId"));
            return;
        }
        if (actionType == AiPendingActionType.APPLY_AI_CATEGORY) {
            PermissionUtil.requireStaffOrAdmin();
            ticketService.getTicketById(requireLong(payload, "ticketId"));
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

    private Long requirePositiveId(Long id, String key) {
        if (id == null) {
            throw new BusinessException(400, key + "不能为空");
        }
        if (id < 1) {
            throw new BusinessException(400, key + "不能小于1");
        }
        return id;
    }

    private String requireRequestText(String value, String key) {
        String text = optionalRequestText(value);
        if (text == null) {
            throw new BusinessException(400, key + "不能为空");
        }
        return text;
    }

    private String optionalRequestText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void requireMaxLength(String value, String key, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400, key + "长度不能超过" + maxLength);
        }
    }

    private void validateOptionalConfidence(Object value) {
        if (value == null) {
            return;
        }
        double confidence;
        if (value instanceof Number number) {
            confidence = number.doubleValue();
        } else {
            try {
                confidence = Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "confidence参数格式不正确");
            }
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new BusinessException(400, "confidence必须在0到1之间");
        }
    }

    private AiPendingActionCreateRequest createPendingRequest(
            String conversationId,
            AiPendingActionType actionType,
            Map<String, Object> payload) {
        AiPendingActionCreateRequest request = new AiPendingActionCreateRequest();
        request.setConversationId(conversationId);
        request.setActionType(actionType.name());
        request.setPayload(payload);
        return request;
    }

    private AiPendingConfirmationResponse toPendingConfirmationResponse(
            String message,
            AiPendingActionResponse pendingAction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", pendingAction.getId());
        data.put("actionType", pendingAction.getActionType());
        data.put("ticketId", pendingAction.getPayload().get("ticketId"));
        data.put("payload", pendingAction.getPayload());

        AiPendingConfirmationResponse response = new AiPendingConfirmationResponse();
        response.setType("PENDING_CONFIRMATION");
        response.setMessage(message);
        response.setData(data);
        response.setRiskFlags(java.util.List.of());
        return response;
    }

    private void recordSpecificPendingCreatedLog(
            AiPendingAction action,
            AiPendingActionType actionType,
            Map<String, Object> payload) {
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            operationLogService.record(
                    OperationType.AI_REPLY_SAVE_PENDING_CREATED.name(),
                    BusinessType.AI_PENDING_ACTION.name(),
                    action.getId(),
                    "用户发起保存 AI 回复确认，工单ID=" + payload.get("ticketId")
            );
            return;
        }
        if (actionType == AiPendingActionType.APPLY_AI_CATEGORY) {
            operationLogService.record(
                    OperationType.AI_CATEGORY_APPLY_PENDING_CREATED.name(),
                    BusinessType.AI_PENDING_ACTION.name(),
                    action.getId(),
                    "用户发起采纳 AI 分类确认，工单ID=" + payload.get("ticketId")
                            + "，分类=" + safeText(String.valueOf(payload.get("category")), 64)
            );
        }
    }

    private void recordSpecificConfirmedLog(
            AiPendingAction action,
            Map<String, Object> payload,
            Object result) {
        if (!AiPendingActionType.APPLY_AI_CATEGORY.name().equals(action.getActionType())) {
            return;
        }

        String oldCategory = "";
        String newCategory = String.valueOf(payload.get("category"));
        Long ticketId = null;
        if (result instanceof Map<?, ?> resultMap) {
            Object idValue = resultMap.get("id");
            ticketId = idValue instanceof Number number ? number.longValue() : null;
            Object oldValue = resultMap.get("oldCategory");
            Object newValue = resultMap.get("newCategory");
            oldCategory = oldValue == null ? "" : String.valueOf(oldValue);
            if (newValue != null) {
                newCategory = String.valueOf(newValue);
            }
        }
        if (ticketId == null) {
            ticketId = requireLong(payload, "ticketId");
        }

        operationLogService.record(
                OperationType.AI_CATEGORY_APPLIED.name(),
                BusinessType.TICKET.name(),
                ticketId,
                "用户确认采纳 AI 分类，工单ID=" + ticketId
                        + "，旧分类=" + displayForLog(oldCategory)
                        + "，新分类=" + displayForLog(newCategory)
        );
    }

    private void recordAiPendingAudit(
            AiPendingAction action,
            String conversationId,
            AiPendingActionType actionType,
            Map<String, Object> payload,
            String resultStatus,
            String resultSummary,
            Object result) {
        String auditActionType = aiAuditActionType(action, actionType, resultStatus);
        Long targetId = targetIdForAudit(actionType, payload, result);
        String businessType = targetId == null ? BusinessType.AI_PENDING_ACTION.name() : BusinessType.TICKET.name();
        Long businessId = targetId == null && action != null ? action.getId() : targetId;
        operationLogService.recordAi(
                auditActionType,
                businessType,
                businessId,
                conversationId,
                intentForAudit(actionType),
                toolNameForAudit(actionType),
                BusinessType.TICKET.name(),
                targetId,
                resultStatus,
                requestSummaryForAudit(actionType, payload),
                resultSummary,
                payload == null ? null : payload.get("riskFlags")
        );
    }

    private String aiAuditActionType(AiPendingAction action, AiPendingActionType actionType, String resultStatus) {
        if ("FORBIDDEN".equals(resultStatus)) {
            return OperationType.AI_FORBIDDEN.name();
        }
        if ("FAILED".equals(resultStatus)) {
            return OperationType.AI_ERROR.name();
        }
        boolean confirmed = action != null && AiPendingActionStatus.CONFIRMED.name().equals(action.getStatus());
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            if ("CANCELLED".equals(resultStatus)) {
                return OperationType.AI_CREATE_TICKET_CANCELLED.name();
            }
            return confirmed ? OperationType.AI_CREATE_TICKET_CONFIRMED.name() : OperationType.AI_CREATE_TICKET_PENDING.name();
        }
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            if ("CANCELLED".equals(resultStatus)) {
                return OperationType.AI_UPDATE_STATUS_CANCELLED.name();
            }
            return confirmed ? OperationType.AI_UPDATE_STATUS_CONFIRMED.name() : OperationType.AI_UPDATE_STATUS_PENDING.name();
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            if ("CANCELLED".equals(resultStatus)) {
                return OperationType.AI_REPLY_CANCELLED.name();
            }
            return confirmed ? OperationType.AI_REPLY_CONFIRMED.name() : OperationType.AI_REPLY_PENDING.name();
        }
        return OperationType.AI_CLASSIFY_TICKET.name();
    }

    private String intentForAudit(AiPendingActionType actionType) {
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            return "CREATE_TICKET";
        }
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            return "UPDATE_TICKET_STATUS";
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            return "SAVE_AI_REPLY";
        }
        return "CLASSIFY_TICKET";
    }

    private String toolNameForAudit(AiPendingActionType actionType) {
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            return "create_ticket";
        }
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            return "update_ticket_status";
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            return "save_ai_reply";
        }
        return "classify_ticket";
    }

    private String requestSummaryForAudit(AiPendingActionType actionType, Map<String, Object> payload) {
        if (payload == null) {
            return actionType.name();
        }
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            return "创建工单：" + safeText(String.valueOf(payload.get("title")), 80);
        }
        if (actionType == AiPendingActionType.UPDATE_TICKET_STATUS) {
            return "修改工单状态：" + payload.get("ticketId") + " -> " + payload.get("status");
        }
        if (actionType == AiPendingActionType.SAVE_AI_REPLY) {
            return "保存 AI 回复：" + payload.get("ticketId");
        }
        return "采纳 AI 分类：" + payload.get("ticketId") + " -> " + payload.get("category");
    }

    private Long targetIdForAudit(AiPendingActionType actionType, Map<String, Object> payload, Object result) {
        if (actionType == AiPendingActionType.CREATE_TICKET && result instanceof Ticket ticket) {
            return ticket.getId();
        }
        if (actionType == AiPendingActionType.CREATE_TICKET) {
            return null;
        }
        if (result instanceof TicketReply reply) {
            return reply.getTicketId();
        }
        if (result instanceof Map<?, ?> resultMap) {
            Object id = resultMap.get("id");
            if (id instanceof Number number) {
                return number.longValue();
            }
        }
        if (payload != null && payload.get("ticketId") instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String displayForLog(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "空" : safeText(text, 64);
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

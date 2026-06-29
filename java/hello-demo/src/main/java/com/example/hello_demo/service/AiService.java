package com.example.hello_demo.service;

import com.example.hello_demo.client.AiClient;
import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 业务服务。
 * 当前阶段只负责转发请求到 Python AI 服务。
 */
@Service
public class AiService {

    private final AiClient aiClient;
    private final OperationLogService operationLogService;

    public AiService(AiClient aiClient, OperationLogService operationLogService) {
        this.aiClient = aiClient;
        this.operationLogService = operationLogService;
    }

    public Map<String, Object> chat(PythonAgentChatRequestDTO requestDTO) {
        try {
            Map<String, Object> response = aiClient.chat(requestDTO);
            recordChatAudit(requestDTO, response);
            return response;
        } catch (BusinessException e) {
            recordChatFailure(requestDTO, e);
            throw e;
        }
    }

    public Map<String, Object> generateReplySuggestion(Long ticketId, String authToken) {
        try {
            Map<String, Object> response = aiClient.generateReplySuggestion(ticketId, authToken);
            operationLogService.recordAi(
                    OperationType.AI_REPLY_SUGGESTION.name(),
                    BusinessType.TICKET.name(),
                    ticketId,
                    null,
                    "GENERATE_REPLY_SUGGESTION",
                    "generate_reply_suggestion",
                    BusinessType.TICKET.name(),
                    ticketId,
                    "SUCCESS",
                    null,
                    "生成 AI 回复建议",
                    response.get("risk_flags")
            );
            return response;
        } catch (BusinessException e) {
            operationLogService.recordAi(
                    e.getCode() == 403 ? OperationType.AI_FORBIDDEN.name() : OperationType.AI_ERROR.name(),
                    BusinessType.TICKET.name(),
                    ticketId,
                    null,
                    "GENERATE_REPLY_SUGGESTION",
                    "generate_reply_suggestion",
                    BusinessType.TICKET.name(),
                    ticketId,
                    e.getCode() == 403 ? "FORBIDDEN" : "FAILED",
                    null,
                    e.getMessage(),
                    null
            );
            throw e;
        }
    }

    private void recordChatAudit(PythonAgentChatRequestDTO requestDTO, Map<String, Object> response) {
        String actionType = firstString(response, "actionType");
        if (actionType == null) {
            actionType = fallbackActionType(response, requestDTO.getMessage());
        }
        if (actionType == null) {
            return;
        }

        String resultStatus = resultStatus(response);
        Long targetId = longValue(response.get("targetId"));
        String targetType = firstString(response, "targetType");
        String businessType = BusinessType.TICKET.name().equals(targetType)
                || OperationType.AI_QUERY_TICKET.name().equals(actionType)
                ? BusinessType.TICKET.name()
                : BusinessType.AI_PENDING_ACTION.name();

        operationLogService.recordAi(
                actionType,
                businessType,
                targetId,
                requestDTO.getConversation_id(),
                firstString(response, "intent"),
                firstString(response, "toolName"),
                targetType,
                targetId,
                resultStatus,
                requestDTO.getMessage(),
                firstString(response, "message", "answer", "error"),
                response.get("risk_flags")
        );
    }

    private void recordChatFailure(PythonAgentChatRequestDTO requestDTO, BusinessException e) {
        int code = e.getCode();
        boolean forbidden = code == 403 || code == 404;
        operationLogService.recordAi(
                forbidden ? OperationType.AI_FORBIDDEN.name() : OperationType.AI_ERROR.name(),
                BusinessType.TICKET.name(),
                null,
                requestDTO.getConversation_id(),
                "UNKNOWN",
                null,
                BusinessType.TICKET.name(),
                null,
                forbidden ? "FORBIDDEN" : "FAILED",
                requestDTO.getMessage(),
                e.getMessage(),
                null
        );
    }

    private String fallbackActionType(Map<String, Object> response, String message) {
        String type = firstString(response, "type");
        if ("FORBIDDEN".equals(type)) {
            return OperationType.AI_FORBIDDEN.name();
        }
        if ("ERROR".equals(type) || "UNAUTHORIZED".equals(type)) {
            return OperationType.AI_ERROR.name();
        }
        if ("PENDING_CONFIRMATION".equals(type)) {
            Object data = response.get("data");
            String pendingType = data instanceof Map<?, ?> map ? stringValue(map.get("actionType")) : null;
            if ("CREATE_TICKET".equals(pendingType)) {
                return OperationType.AI_CREATE_TICKET_PENDING.name();
            }
            if ("UPDATE_TICKET_STATUS".equals(pendingType)) {
                return OperationType.AI_UPDATE_STATUS_PENDING.name();
            }
            if ("SAVE_AI_REPLY".equals(pendingType)) {
                return OperationType.AI_REPLY_PENDING.name();
            }
        }
        String answer = extractAnswer(response);
        if (isConfirmMessage(message) && isSuccessfulConfirmedWriteAnswer(answer)) {
            return OperationType.AI_WRITE_CONFIRMED.name();
        }
        if (isCancelMessage(message) && isSuccessfulCancelledWriteAnswer(answer)) {
            return OperationType.AI_WRITE_CANCELLED.name();
        }
        if (isTicketQuery(message)) {
            return OperationType.AI_QUERY_TICKET.name();
        }
        return null;
    }

    private String extractAnswer(Map<String, Object> response) {
        Object answer = response.get("answer");
        return answer == null ? "" : String.valueOf(answer);
    }

    private boolean isSuccessfulConfirmedWriteAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        return normalized.startsWith("已创建工单")
                || normalized.startsWith("已将 ")
                || normalized.startsWith("已保存 ");
    }

    private boolean isSuccessfulCancelledWriteAnswer(String answer) {
        String normalized = answer == null ? "" : answer.trim();
        return normalized.startsWith("已取消");
    }

    private boolean isConfirmMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.equals("确认") || normalized.equals("确定")
                || normalized.equals("yes") || normalized.equals("ok");
    }

    private boolean isCancelMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.equals("取消") || normalized.equals("不用")
                || normalized.equals("cancel") || normalized.equals("no");
    }

    private boolean isTicketQuery(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        if (!normalized.contains("工单")) {
            return false;
        }
        return normalized.contains("查") || normalized.contains("查询")
                || normalized.contains("找") || normalized.contains("列出")
                || normalized.contains("所有") || normalized.contains("全部");
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

    private String resultStatus(Map<String, Object> response) {
        String type = firstString(response, "type");
        if ("FORBIDDEN".equals(type)) {
            return "FORBIDDEN";
        }
        if ("ERROR".equals(type) || "UNAUTHORIZED".equals(type)) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private String firstString(Map<String, Object> response, String... keys) {
        for (String key : keys) {
            String value = stringValue(response.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : safeText(text, 200);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

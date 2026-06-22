package com.example.hello_demo.service;

import com.example.hello_demo.client.AiClient;
import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
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
        Map<String, Object> response = aiClient.chat(requestDTO);
        recordChatAudit(requestDTO, response);
        return response;
    }

    public Map<String, Object> generateReplySuggestion(Long ticketId, String authToken) {
        Map<String, Object> response = aiClient.generateReplySuggestion(ticketId, authToken);
        operationLogService.record(
                OperationType.AI_REPLY_SUGGESTED.name(),
                BusinessType.TICKET.name(),
                ticketId,
                "生成 AI 回复建议"
        );
        return response;
    }

    private void recordChatAudit(PythonAgentChatRequestDTO requestDTO, Map<String, Object> response) {
        String message = requestDTO.getMessage();
        String answer = extractAnswer(response);
        if (isConfirmMessage(message) && isSuccessfulConfirmedWriteAnswer(answer)) {
            operationLogService.record(
                    OperationType.AI_WRITE_CONFIRMED.name(),
                    BusinessType.TICKET.name(),
                    null,
                    "确认执行 AI 写操作，conversationId=" + safeText(requestDTO.getConversation_id(), 80)
            );
            return;
        }
        if (isCancelMessage(message) && isSuccessfulCancelledWriteAnswer(answer)) {
            operationLogService.record(
                    OperationType.AI_WRITE_CANCELLED.name(),
                    BusinessType.TICKET.name(),
                    null,
                    "取消 AI 写操作，conversationId=" + safeText(requestDTO.getConversation_id(), 80)
            );
            return;
        }
        if (isTicketQuery(message)) {
            operationLogService.record(
                    OperationType.AI_TICKET_QUERY.name(),
                    BusinessType.TICKET.name(),
                    null,
                    "AI 查询工单：" + safeText(message, 200)
            );
        }
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
}

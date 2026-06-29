package com.example.hello_demo.service;

import com.example.hello_demo.client.AiClient;
import com.example.hello_demo.dto.PythonAgentChatRequestDTO;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceAuditTest {

    private AiClient aiClient;
    private OperationLogService operationLogService;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        operationLogService = mock(OperationLogService.class);
        aiService = new AiService(aiClient, operationLogService);
    }

    @Test
    void ticketQueryIsAudited() {
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO(
                "查询我的工单",
                "chat-1",
                1L,
                "Bearer token"
        );
        when(aiClient.chat(request)).thenReturn(Map.of("answer", "共找到 1 个工单"));

        Map<String, Object> response = aiService.chat(request);

        assertEquals("共找到 1 个工单", response.get("answer"));
        verify(operationLogService).recordAi(
                eq(OperationType.AI_QUERY_TICKET.name()),
                eq(BusinessType.TICKET.name()),
                isNull(),
                eq("chat-1"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("SUCCESS"),
                eq("查询我的工单"),
                eq("共找到 1 个工单"),
                any()
        );
    }

    @Test
    void confirmedWriteIsAuditedWhenPythonExecutedPendingAction() {
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO(
                "确认",
                "chat-2",
                1L,
                "Bearer token"
        );
        when(aiClient.chat(request)).thenReturn(Map.of("answer", "已创建工单：ID 10"));

        aiService.chat(request);

        verify(operationLogService).recordAi(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                isNull(),
                eq("chat-2"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("SUCCESS"),
                eq("确认"),
                contains("已创建工单"),
                any()
        );
    }

    @Test
    void emptyConfirmIsNotAuditedAsWrite() {
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO(
                "确认",
                "chat-3",
                1L,
                "Bearer token"
        );
        when(aiClient.chat(request)).thenReturn(Map.of("answer", "当前没有待确认的操作，请重新发起请求。"));

        aiService.chat(request);

        verify(operationLogService, never()).recordAi(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                isNull(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void loginExpiredConfirmIsNotAuditedAsConfirmedWrite() {
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO(
                "确认",
                "chat-token-expired",
                1L,
                "Bearer expired-token"
        );
        when(aiClient.chat(request)).thenReturn(Map.of("answer", "登录状态已失效，请重新登录。"));

        aiService.chat(request);

        verify(operationLogService, never()).recordAi(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                isNull(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void cancelledWriteIsAuditedWhenPythonClearedPendingAction() {
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO(
                "取消",
                "chat-4",
                1L,
                "Bearer token"
        );
        when(aiClient.chat(request)).thenReturn(Map.of("answer", "已取消本次操作。"));

        aiService.chat(request);

        verify(operationLogService).recordAi(
                eq(OperationType.AI_WRITE_CANCELLED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                isNull(),
                eq("chat-4"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("SUCCESS"),
                eq("取消"),
                contains("已取消"),
                any()
        );
    }

    @Test
    void replySuggestionIsAudited() {
        Map<String, Object> aiResponse = Map.of("suggestion", "建议回复内容");
        when(aiClient.generateReplySuggestion(3L, "Bearer token")).thenReturn(aiResponse);

        Map<String, Object> response = aiService.generateReplySuggestion(3L, "Bearer token");

        assertEquals(aiResponse, response);
        verify(operationLogService).recordAi(
                eq(OperationType.AI_REPLY_SUGGESTION.name()),
                eq(BusinessType.TICKET.name()),
                eq(3L),
                isNull(),
                eq("GENERATE_REPLY_SUGGESTION"),
                eq("generate_reply_suggestion"),
                eq(BusinessType.TICKET.name()),
                eq(3L),
                eq("SUCCESS"),
                isNull(),
                eq("生成 AI 回复建议"),
                any()
        );
    }
}

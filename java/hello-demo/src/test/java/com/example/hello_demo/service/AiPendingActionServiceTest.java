package com.example.hello_demo.service;

import com.example.hello_demo.dto.AiPendingActionConfirmResponse;
import com.example.hello_demo.dto.AiPendingActionCreateRequest;
import com.example.hello_demo.dto.AiPendingConfirmationResponse;
import com.example.hello_demo.dto.AiPendingActionResponse;
import com.example.hello_demo.dto.AiCategoryPendingRequest;
import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.dto.AiReplyPendingRequest;
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
import com.example.hello_demo.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPendingActionServiceTest {

    private AiPendingActionMapper aiPendingActionMapper;
    private TicketService ticketService;
    private TicketReplyService ticketReplyService;
    private OperationLogService operationLogService;
    private AiPendingActionService aiPendingActionService;

    @BeforeEach
    void setUp() {
        aiPendingActionMapper = mock(AiPendingActionMapper.class);
        ticketService = mock(TicketService.class);
        ticketReplyService = mock(TicketReplyService.class);
        operationLogService = mock(OperationLogService.class);
        aiPendingActionService = new AiPendingActionService(
                aiPendingActionMapper,
                ticketService,
                ticketReplyService,
                operationLogService
        );
        CurrentUserContext.set(7L, "staff", "STAFF");
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void createPendingActionPersistsBusinessPayloadOnly() {
        when(aiPendingActionMapper.insert(any(AiPendingAction.class))).thenAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(10L);
            return 1;
        });

        AiPendingActionResponse response = aiPendingActionService.createPendingAction(
                createRequest("chat-1", AiPendingActionType.CREATE_TICKET, createTicketPayload())
        );

        assertEquals(10L, response.getId());
        assertEquals(7L, response.getUserId());
        assertEquals("chat-1", response.getConversationId());
        assertEquals(AiPendingActionType.CREATE_TICKET.name(), response.getActionType());
        assertEquals(AiPendingActionStatus.PENDING.name(), response.getStatus());
        assertEquals("登录失败", response.getPayload().get("title"));
        verify(operationLogService).record(
                eq(OperationType.AI_PENDING_ACTION_CREATED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(10L),
                contains("CREATE_TICKET")
        );
    }

    @Test
    void currentPendingActionIsLoadedByCurrentUserAndConversation() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(11L, 7L, "chat-2", AiPendingActionType.UPDATE_TICKET_STATUS, updateStatusPayload())
        );

        AiPendingActionResponse response = aiPendingActionService.getCurrentPendingAction("chat-2");

        assertNotNull(response);
        assertEquals(11L, response.getId());
        assertEquals("chat-2", response.getConversationId());
        assertEquals("PROCESSING", response.getPayload().get("status"));
    }

    @Test
    void confirmCreateTicketExecutesBusinessActionAndMarksConfirmed() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(12L, 7L, "chat-3", AiPendingActionType.CREATE_TICKET, createTicketPayload())
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);
        when(ticketService.createTicket(any(TicketCreateDTO.class))).thenReturn(ticket(20L, "OPEN"));

        AiPendingActionConfirmResponse response = aiPendingActionService.confirmPendingAction("chat-3");

        assertEquals(AiPendingActionStatus.CONFIRMED.name(), response.getPendingAction().getStatus());
        assertEquals(20L, ((Ticket) response.getResult()).getId());

        ArgumentCaptor<TicketCreateDTO> captor = ArgumentCaptor.forClass(TicketCreateDTO.class);
        verify(ticketService).createTicket(captor.capture());
        assertEquals("登录失败", captor.getValue().getTitle());
        assertEquals("用户无法登录", captor.getValue().getContent());
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(12L),
                contains("chat-3")
        );
    }

    @Test
    void cancelPendingActionDoesNotExecuteBusinessAction() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(13L, 7L, "chat-4", AiPendingActionType.UPDATE_TICKET_STATUS, updateStatusPayload())
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);

        AiPendingActionResponse response = aiPendingActionService.cancelPendingAction("chat-4");

        assertEquals(AiPendingActionStatus.CANCELLED.name(), response.getStatus());
        verify(ticketService, never()).updateTicketStatus(any(), any());
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CANCELLED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(13L),
                contains("chat-4")
        );
    }

    @Test
    void repeatedConfirmDoesNotExecuteTwice() {
        when(aiPendingActionMapper.selectOne(any()))
                .thenReturn(pendingAction(14L, 7L, "chat-5", AiPendingActionType.CREATE_TICKET, createTicketPayload()))
                .thenReturn(null);
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);
        when(ticketService.createTicket(any(TicketCreateDTO.class))).thenReturn(ticket(21L, "OPEN"));

        aiPendingActionService.confirmPendingAction("chat-5");
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-5")
        );

        assertEquals(400, exception.getCode());
        verify(ticketService).createTicket(any(TicketCreateDTO.class));
    }

    @Test
    void confirmDoesNotExecuteWhenAtomicStatusUpdateFails() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(141L, 7L, "chat-race", AiPendingActionType.CREATE_TICKET, createTicketPayload())
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-race")
        );

        assertEquals(400, exception.getCode());
        assertEquals("当前没有待确认的操作，请重新发起请求。", exception.getMessage());
        verify(ticketService, never()).createTicket(any(TicketCreateDTO.class));
        verify(operationLogService, never()).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(141L),
                any()
        );
    }

    @Test
    void otherUserCannotConfirmPendingAction() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-6")
        );

        assertEquals(400, exception.getCode());
        verify(ticketService, never()).createTicket(any(TicketCreateDTO.class));
    }

    @Test
    void confirmWithoutCurrentUserFailsBeforeLoadingPendingAction() {
        CurrentUserContext.clear();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-6")
        );

        assertEquals(401, exception.getCode());
        assertEquals("登录状态已失效，请重新登录。", exception.getMessage());
        verify(aiPendingActionMapper, never()).selectOne(any());
        verify(aiPendingActionMapper, never()).update(any(), any());
        verify(ticketService, never()).createTicket(any(TicketCreateDTO.class));
    }

    @Test
    void confirmRejectsPendingActionOwnedByAnotherUser() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(16L, 99L, "chat-6", AiPendingActionType.CREATE_TICKET, createTicketPayload())
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-6")
        );

        assertEquals(403, exception.getCode());
        assertEquals("你没有权限执行该操作。", exception.getMessage());
        verify(aiPendingActionMapper, never()).update(any(), any());
        verify(ticketService, never()).createTicket(any(TicketCreateDTO.class));
    }

    @Test
    void expiredPendingActionCannotBeConfirmedOrExecuted() {
        AiPendingAction expiredAction = pendingAction(
                17L,
                7L,
                "chat-expired",
                AiPendingActionType.CREATE_TICKET,
                createTicketPayload()
        );
        expiredAction.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        when(aiPendingActionMapper.selectOne(any())).thenReturn(expiredAction);
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("chat-expired")
        );

        assertEquals(400, exception.getCode());
        assertEquals("待确认操作已过期，请重新发起请求。", exception.getMessage());
        verify(ticketService, never()).createTicket(any(TicketCreateDTO.class));
    }

    @Test
    void getCurrentPendingActionMarksExpiredActionAndReturnsNull() {
        AiPendingAction expiredAction = pendingAction(
                18L,
                7L,
                "chat-expired-current",
                AiPendingActionType.UPDATE_TICKET_STATUS,
                updateStatusPayload()
        );
        expiredAction.setCreatedAt(LocalDateTime.now().minusMinutes(11));
        when(aiPendingActionMapper.selectOne(any())).thenReturn(expiredAction);
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);

        AiPendingActionResponse response = aiPendingActionService.getCurrentPendingAction("chat-expired-current");

        assertEquals(null, response);
        verify(ticketService, never()).updateTicketStatus(any(), any());
    }

    @Test
    void otherConversationCannotConfirmPendingAction() {
        when(aiPendingActionMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.confirmPendingAction("other-chat")
        );

        assertEquals(400, exception.getCode());
        verify(ticketService, never()).updateTicketStatus(any(), any());
    }

    @Test
    void pendingActionRejectsTokenInPayload() {
        Map<String, Object> payload = createTicketPayload();
        payload.put("auth_token", "Bearer secret");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.createPendingAction(
                        createRequest("chat-7", AiPendingActionType.CREATE_TICKET, payload)
                )
        );

        assertEquals(400, exception.getCode());
        verify(aiPendingActionMapper, never()).insert(any(AiPendingAction.class));
    }

    @Test
    void staffCreatesSaveAiReplyPendingAction() {
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1L, "OPEN"));
        when(aiPendingActionMapper.insert(any(AiPendingAction.class))).thenAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(40L);
            return 1;
        });
        AiReplyPendingRequest request = new AiReplyPendingRequest();
        request.setConversationId("chat-ai-reply");
        request.setContent("建议先补充错误截图。");

        AiPendingConfirmationResponse response = aiPendingActionService.createSaveAiReplyPending(1L, request);

        assertEquals("PENDING_CONFIRMATION", response.getType());
        assertEquals("请确认是否保存这条 AI 回复。", response.getMessage());
        assertEquals(AiPendingActionType.SAVE_AI_REPLY.name(), response.getData().get("actionType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getData().get("payload");
        assertEquals("建议先补充错误截图。", payload.get("content"));
        assertFalse(payload.containsKey("token"));
        verify(operationLogService).record(
                eq(OperationType.AI_REPLY_SAVE_PENDING_CREATED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(40L),
                contains("工单ID=1")
        );
    }

    @Test
    void normalUserCannotCreateSaveAiReplyPendingAction() {
        CurrentUserContext.set(1L, "tom", "USER");
        AiReplyPendingRequest request = new AiReplyPendingRequest();
        request.setConversationId("chat-ai-reply-user");
        request.setContent("AI reply");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.createSaveAiReplyPending(1L, request)
        );

        assertEquals(403, exception.getCode());
        verify(aiPendingActionMapper, never()).insert(any(AiPendingAction.class));
        verify(ticketReplyService, never()).createAiReply(any(), any());
    }

    @Test
    void saveAiReplyPendingRejectsTooLongContent() {
        AiReplyPendingRequest request = new AiReplyPendingRequest();
        request.setConversationId("chat-ai-reply-long");
        request.setContent("x".repeat(2001));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.createSaveAiReplyPending(1L, request)
        );

        assertEquals(400, exception.getCode());
        verify(aiPendingActionMapper, never()).insert(any(AiPendingAction.class));
    }

    @Test
    void staffCreatesApplyCategoryPendingAction() {
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1L, "OPEN"));
        when(aiPendingActionMapper.insert(any(AiPendingAction.class))).thenAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(41L);
            return 1;
        });
        AiCategoryPendingRequest request = new AiCategoryPendingRequest();
        request.setConversationId("chat-category");
        request.setCategory("账号登录");
        request.setConfidence(0.82);
        request.setReason("标题和描述均提到登录失败");

        AiPendingConfirmationResponse response = aiPendingActionService.createApplyCategoryPending(1L, request);

        assertEquals("PENDING_CONFIRMATION", response.getType());
        assertEquals(AiPendingActionType.APPLY_AI_CATEGORY.name(), response.getData().get("actionType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getData().get("payload");
        assertEquals("账号登录", payload.get("category"));
        assertEquals(0.82, payload.get("confidence"));
        verify(operationLogService).record(
                eq(OperationType.AI_CATEGORY_APPLY_PENDING_CREATED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(41L),
                contains("分类=账号登录")
        );
    }

    @Test
    void normalUserCannotCreateApplyCategoryPendingAction() {
        CurrentUserContext.set(1L, "tom", "USER");
        AiCategoryPendingRequest request = new AiCategoryPendingRequest();
        request.setConversationId("chat-category-user");
        request.setCategory("账号登录");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> aiPendingActionService.createApplyCategoryPending(1L, request)
        );

        assertEquals(403, exception.getCode());
        verify(aiPendingActionMapper, never()).insert(any(AiPendingAction.class));
    }

    @Test
    void confirmSaveAiReplyExecutesThroughTicketReplyService() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 1L);
        payload.put("content", "建议先补充错误截图。");
        payload.put("confidence", 0.7);
        payload.put("reason", "工单描述不足，需要更多上下文");
        payload.put("riskFlags", "信息不足,需要人工确认");
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(15L, 7L, "chat-8", AiPendingActionType.SAVE_AI_REPLY, payload)
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);
        TicketReply reply = new TicketReply();
        reply.setId(30L);
        reply.setTicketId(1L);
        reply.setContent("建议先补充错误截图。");
        when(ticketReplyService.createAiReply(eq(1L), any(AiReplyCreateDTO.class))).thenReturn(reply);

        AiPendingActionConfirmResponse response = aiPendingActionService.confirmPendingAction("chat-8");

        assertEquals(30L, ((TicketReply) response.getResult()).getId());
        ArgumentCaptor<AiReplyCreateDTO> captor = ArgumentCaptor.forClass(AiReplyCreateDTO.class);
        verify(ticketReplyService).createAiReply(eq(1L), captor.capture());
        assertEquals("建议先补充错误截图。", captor.getValue().getContent());
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(15L),
                contains("confidence=0.7")
        );
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(15L),
                contains("riskFlags=信息不足,需要人工确认")
        );
    }

    @Test
    void confirmApplyCategoryExecutesThroughTicketServiceAndWritesAiLog() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 1L);
        payload.put("category", "账号登录");
        payload.put("confidence", 0.82);
        payload.put("reason", "标题和描述均提到登录失败");
        Ticket before = ticket(1L, "OPEN");
        before.setCategory("OTHER");
        Ticket after = ticket(1L, "OPEN");
        after.setCategory("账号登录");
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(42L, 7L, "chat-category-confirm", AiPendingActionType.APPLY_AI_CATEGORY, payload)
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);
        when(ticketService.getTicketById(1L)).thenReturn(before);
        when(ticketService.updateTicketCategory(eq(1L), any(TicketCategoryUpdateRequest.class))).thenReturn(after);

        AiPendingActionConfirmResponse response = aiPendingActionService.confirmPendingAction("chat-category-confirm");

        assertEquals(AiPendingActionStatus.CONFIRMED.name(), response.getPendingAction().getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertEquals("OTHER", result.get("oldCategory"));
        assertEquals("账号登录", result.get("newCategory"));
        ArgumentCaptor<TicketCategoryUpdateRequest> captor = ArgumentCaptor.forClass(TicketCategoryUpdateRequest.class);
        verify(ticketService).updateTicketCategory(eq(1L), captor.capture());
        assertEquals("账号登录", captor.getValue().getCategory());
        verify(operationLogService).record(
                eq(OperationType.AI_CATEGORY_APPLIED.name()),
                eq(BusinessType.TICKET.name()),
                eq(1L),
                contains("新分类=账号登录")
        );
    }

    @Test
    void cancelApplyCategoryDoesNotUpdateTicketCategory() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 1L);
        payload.put("category", "账号登录");
        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(43L, 7L, "chat-category-cancel", AiPendingActionType.APPLY_AI_CATEGORY, payload)
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);

        AiPendingActionResponse response = aiPendingActionService.cancelPendingAction("chat-category-cancel");

        assertEquals(AiPendingActionStatus.CANCELLED.name(), response.getStatus());
        verify(ticketService, never()).updateTicketCategory(any(), any());
        verify(operationLogService).record(
                eq(OperationType.AI_ACTION_CANCELLED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(43L),
                contains(AiPendingActionType.APPLY_AI_CATEGORY.name())
        );
    }

    @Test
    void saveAiReplyPendingStoresEditedSuggestionAsReplyContent() {
        when(ticketService.getTicketById(1L)).thenReturn(ticket(1L, "OPEN"));
        when(aiPendingActionMapper.insert(any(AiPendingAction.class))).thenAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(44L);
            return 1;
        });

        AiReplyPendingRequest request = new AiReplyPendingRequest();
        request.setConversationId("chat-edited-reply");
        request.setSuggestion("STAFF edited final reply");
        request.setOriginalSuggestion("Original AI suggestion");
        request.setConfidence(0.82);
        request.setReason("AI generated reason");
        request.setRiskFlags(java.util.List.of("needs human review"));

        AiPendingConfirmationResponse response = aiPendingActionService.createSaveAiReplyPending(1L, request);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) response.getData().get("payload");
        assertEquals("STAFF edited final reply", payload.get("replyContent"));
        assertEquals("STAFF edited final reply", payload.get("content"));
        assertEquals("Original AI suggestion", payload.get("originalSuggestion"));
        assertEquals(0.82, payload.get("confidence"));
        assertEquals("AI generated reason", payload.get("reason"));
        assertEquals(java.util.List.of("needs human review"), payload.get("riskFlags"));
    }

    @Test
    void confirmSaveAiReplyUsesReplyContentBeforeLegacyContent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 1L);
        payload.put("replyContent", "STAFF edited final reply");
        payload.put("content", "Original AI suggestion");
        payload.put("originalSuggestion", "Original AI suggestion");

        when(aiPendingActionMapper.selectOne(any())).thenReturn(
                pendingAction(45L, 7L, "chat-edited-confirm", AiPendingActionType.SAVE_AI_REPLY, payload)
        );
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenReturn(1);
        TicketReply reply = new TicketReply();
        reply.setId(31L);
        reply.setTicketId(1L);
        reply.setContent("STAFF edited final reply");
        when(ticketReplyService.createAiReply(eq(1L), any(AiReplyCreateDTO.class))).thenReturn(reply);

        aiPendingActionService.confirmPendingAction("chat-edited-confirm");

        ArgumentCaptor<AiReplyCreateDTO> captor = ArgumentCaptor.forClass(AiReplyCreateDTO.class);
        verify(ticketReplyService).createAiReply(eq(1L), captor.capture());
        assertEquals("STAFF edited final reply", captor.getValue().getContent());
    }

    private AiPendingActionCreateRequest createRequest(
            String conversationId,
            AiPendingActionType actionType,
            Map<String, Object> payload) {
        AiPendingActionCreateRequest request = new AiPendingActionCreateRequest();
        request.setConversationId(conversationId);
        request.setActionType(actionType.name());
        request.setPayload(payload);
        return request;
    }

    private Map<String, Object> createTicketPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "登录失败");
        payload.put("content", "用户无法登录");
        payload.put("priority", "HIGH");
        return payload;
    }

    private Map<String, Object> updateStatusPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", 1L);
        payload.put("status", "PROCESSING");
        return payload;
    }

    private AiPendingAction pendingAction(
            Long id,
            Long userId,
            String conversationId,
            AiPendingActionType actionType,
            Map<String, Object> payload) {
        AiPendingAction action = new AiPendingAction();
        action.setId(id);
        action.setUserId(userId);
        action.setConversationId(conversationId);
        action.setActionType(actionType.name());
        action.setPayloadJson(toJson(payload));
        action.setStatus(AiPendingActionStatus.PENDING.name());
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        return action;
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private Ticket ticket(Long id, String status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle("登录失败");
        ticket.setContent("用户无法登录");
        ticket.setPriority("HIGH");
        ticket.setStatus(status);
        ticket.setUserId(7L);
        return ticket;
    }
}

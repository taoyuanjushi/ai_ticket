package com.example.hello_demo.controller;

import com.example.hello_demo.entity.AiPendingAction;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.enums.AiPendingActionStatus;
import com.example.hello_demo.enums.AiPendingActionType;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.mapper.AiPendingActionMapper;
import com.example.hello_demo.service.AiPendingActionService;
import com.example.hello_demo.service.OperationLogService;
import com.example.hello_demo.service.TicketReplyService;
import com.example.hello_demo.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PendingActionIntegrationTest extends MockMvcIntegrationTestSupport {

    private AiPendingActionMapper aiPendingActionMapper;
    private TicketService ticketService;
    private TicketReplyService ticketReplyService;
    private OperationLogService operationLogService;
    private AtomicReference<AiPendingAction> storedAction;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        aiPendingActionMapper = mock(AiPendingActionMapper.class);
        ticketService = mock(TicketService.class);
        ticketReplyService = mock(TicketReplyService.class);
        operationLogService = mock(OperationLogService.class);
        storedAction = new AtomicReference<>();
        stubInMemoryPendingActionTable();

        AiPendingActionService service = new AiPendingActionService(
                aiPendingActionMapper,
                ticketService,
                ticketReplyService,
                operationLogService
        );
        mockMvc = mockMvc(new AiPendingActionController(service));
    }

    @Test
    void staffCanCreatePendingAndConfirm() throws Exception {
        when(ticketService.getTicketById(3L)).thenReturn(ticket(3L, "OPEN"));
        when(ticketService.updateTicketStatus(3L, "PROCESSING")).thenReturn(ticket(3L, "PROCESSING"));

        createUpdateStatusPending("test-staff-pending-001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.conversationId").value("test-staff-pending-001"))
                .andExpect(content().string(not(containsString("token"))));

        org.junit.jupiter.api.Assertions.assertEquals(7L, storedAction.get().getUserId());
        org.junit.jupiter.api.Assertions.assertEquals("test-staff-pending-001", storedAction.get().getConversationId());
        org.junit.jupiter.api.Assertions.assertFalse(storedAction.get().getPayloadJson().toLowerCase().contains("token"));

        mockMvc.perform(post("/ai/pending-actions/confirm")
                        .header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"test-staff-pending-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingAction.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.result.oldStatus").value("OPEN"))
                .andExpect(jsonPath("$.data.result.newStatus").value("PROCESSING"));

        verify(ticketService).updateTicketStatus(3L, "PROCESSING");
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(storedAction.get().getId()),
                contains("test-staff-pending-001")
        );
    }

    @Test
    void repeatConfirmDoesNotExecuteTwice() throws Exception {
        when(ticketService.getTicketById(3L)).thenReturn(ticket(3L, "OPEN"));
        when(ticketService.updateTicketStatus(3L, "PROCESSING")).thenReturn(ticket(3L, "PROCESSING"));

        createUpdateStatusPending("repeat-confirm-001").andExpect(status().isOk());

        mockMvc.perform(post("/ai/pending-actions/confirm")
                        .header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"repeat-confirm-001\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/ai/pending-actions/confirm")
                        .header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"repeat-confirm-001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("当前没有待确认的操作，请重新发起请求。"));

        verify(ticketService).updateTicketStatus(3L, "PROCESSING");
        ArgumentCaptor<String> logContentCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(storedAction.get().getId()),
                logContentCaptor.capture()
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, logContentCaptor.getAllValues().size());
    }

    @Test
    void cancelPendingDoesNotExecuteAction() throws Exception {
        when(ticketService.getTicketById(3L)).thenReturn(ticket(3L, "OPEN"));

        createUpdateStatusPending("cancel-pending-001").andExpect(status().isOk());

        mockMvc.perform(post("/ai/pending-actions/cancel")
                        .header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"cancel-pending-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        org.junit.jupiter.api.Assertions.assertEquals(
                AiPendingActionStatus.CANCELLED.name(),
                storedAction.get().getStatus()
        );

        mockMvc.perform(post("/ai/pending-actions/confirm")
                        .header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"cancel-pending-001\"}"))
                .andExpect(status().isBadRequest());

        verify(ticketService, never()).updateTicketStatus(any(), any());
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CANCELLED.name()),
                eq(BusinessType.AI_PENDING_ACTION.name()),
                eq(storedAction.get().getId()),
                contains("cancel-pending-001")
        );
    }

    private org.springframework.test.web.servlet.ResultActions createUpdateStatusPending(String conversationId) throws Exception {
        return mockMvc.perform(post("/ai/pending-actions")
                .header("Authorization", staffToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "conversationId": "%s",
                          "actionType": "UPDATE_TICKET_STATUS",
                          "payload": {
                            "ticketId": 3,
                            "status": "PROCESSING"
                          }
                        }
                        """.formatted(conversationId)));
    }

    private void stubInMemoryPendingActionTable() {
        AtomicLong nextId = new AtomicLong(100L);
        when(aiPendingActionMapper.insert(any(AiPendingAction.class))).thenAnswer(invocation -> {
            AiPendingAction action = invocation.getArgument(0);
            action.setId(nextId.getAndIncrement());
            storedAction.set(action);
            return 1;
        });
        when(aiPendingActionMapper.selectOne(any())).thenAnswer(invocation -> {
            AiPendingAction action = storedAction.get();
            if (action != null && AiPendingActionStatus.PENDING.name().equals(action.getStatus())) {
                return action;
            }
            return null;
        });
        when(aiPendingActionMapper.update(any(AiPendingAction.class), any())).thenAnswer(invocation -> {
            AiPendingAction action = storedAction.get();
            AiPendingAction patch = invocation.getArgument(0);
            if (action == null || !AiPendingActionStatus.PENDING.name().equals(action.getStatus())) {
                return 0;
            }
            if (patch.getStatus() != null) {
                action.setStatus(patch.getStatus());
            }
            if (patch.getUpdatedAt() != null) {
                action.setUpdatedAt(patch.getUpdatedAt());
            }
            if (patch.getConfirmedAt() != null) {
                action.setConfirmedAt(patch.getConfirmedAt());
            }
            if (patch.getCancelledAt() != null) {
                action.setCancelledAt(patch.getCancelledAt());
            }
            return 1;
        });
    }

    private Ticket ticket(Long id, String status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setTitle("staff 可处理工单");
        ticket.setContent("需要工作人员处理");
        ticket.setStatus(status);
        ticket.setPriority("HIGH");
        ticket.setUserId(1L);
        ticket.setCreatedAt(LocalDateTime.now());
        return ticket;
    }
}

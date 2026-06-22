package com.example.hello_demo.service;

import com.example.hello_demo.dto.AiReplyCreateDTO;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.enums.BusinessType;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.enums.TicketReplyType;
import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TicketReplyServiceTest {

    private TicketReplyMapper ticketReplyMapper;
    private TicketMapper ticketMapper;
    private OperationLogService operationLogService;
    private TicketCacheService ticketCacheService;
    private TicketReplyService ticketReplyService;

    @BeforeEach
    void setUp() {
        ticketReplyMapper = mock(TicketReplyMapper.class);
        ticketMapper = mock(TicketMapper.class);
        operationLogService = mock(OperationLogService.class);
        ticketCacheService = mock(TicketCacheService.class);
        ticketReplyService = new TicketReplyService(
                ticketReplyMapper,
                ticketMapper,
                operationLogService,
                ticketCacheService
        );
    }

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void staffCanSaveAiReplySuggestion() {
        CurrentUserContext.set(2L, "staff", "STAFF");
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 1L, TicketStatus.OPEN.name()));

        AtomicReference<TicketReply> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            TicketReply reply = invocation.getArgument(0);
            reply.setId(10L);
            inserted.set(reply);
            return 1;
        }).when(ticketReplyMapper).insert(any(TicketReply.class));
        when(ticketReplyMapper.selectById(10L)).thenAnswer(invocation -> inserted.get());

        AiReplyCreateDTO dto = new AiReplyCreateDTO();
        dto.setContent("  请先重启设备后再试  ");

        TicketReply saved = ticketReplyService.createAiReply(1L, dto);

        assertNotNull(saved);
        assertEquals(10L, saved.getId());
        assertEquals(1L, saved.getTicketId());
        assertEquals(2L, saved.getUserId());
        assertEquals("请先重启设备后再试", saved.getContent());
        assertEquals(TicketReplyType.AI.name(), saved.getReplyType());
        verify(operationLogService).record(
                eq(OperationType.AI_WRITE_CONFIRMED.name()),
                eq(BusinessType.TICKET_REPLY.name()),
                eq(10L),
                eq("确认保存 AI 回复建议，工单 #1")
        );
        verify(operationLogService).record(
                eq(OperationType.AI_REPLY_CREATED.name()),
                eq(BusinessType.TICKET_REPLY.name()),
                eq(10L),
                eq("保存 AI 回复建议")
        );
        verify(ticketCacheService).evictTicketRelated(1L);
    }

    @Test
    void normalUserCannotSaveAiReplySuggestion() {
        CurrentUserContext.set(1L, "tom", "USER");

        AiReplyCreateDTO dto = new AiReplyCreateDTO();
        dto.setContent("AI reply");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketReplyService.createAiReply(1L, dto)
        );
        assertEquals(403, exception.getCode());
        verifyNoInteractions(ticketMapper, ticketReplyMapper, operationLogService, ticketCacheService);
    }

    @Test
    void closedTicketCannotReceiveAiReply() {
        CurrentUserContext.set(2L, "staff", "STAFF");
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 1L, TicketStatus.CLOSED.name()));

        AiReplyCreateDTO dto = new AiReplyCreateDTO();
        dto.setContent("AI reply");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketReplyService.createAiReply(1L, dto)
        );
        assertEquals(400, exception.getCode());
        verify(ticketReplyMapper, never()).insert(any(TicketReply.class));
        verifyNoInteractions(operationLogService, ticketCacheService);
    }

    @Test
    void missingTicketReturns404() {
        CurrentUserContext.set(2L, "staff", "STAFF");
        when(ticketMapper.selectById(99L)).thenReturn(null);

        AiReplyCreateDTO dto = new AiReplyCreateDTO();
        dto.setContent("AI reply");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketReplyService.createAiReply(99L, dto)
        );
        assertEquals(404, exception.getCode());
    }

    @Test
    void savedAiReplyIgnoresAnyExternalUserIdentity() {
        CurrentUserContext.set(8L, "admin", "ADMIN");
        when(ticketMapper.selectById(1L)).thenReturn(ticket(1L, 1L, TicketStatus.OPEN.name()));
        doAnswer(invocation -> {
            TicketReply reply = invocation.getArgument(0);
            reply.setId(11L);
            return 1;
        }).when(ticketReplyMapper).insert(any(TicketReply.class));
        when(ticketReplyMapper.selectById(11L)).thenReturn(new TicketReply());

        AiReplyCreateDTO dto = new AiReplyCreateDTO();
        dto.setContent("AI reply");

        ticketReplyService.createAiReply(1L, dto);

        ArgumentCaptor<TicketReply> captor = ArgumentCaptor.forClass(TicketReply.class);
        verify(ticketReplyMapper).insert(captor.capture());
        assertEquals(8L, captor.getValue().getUserId());
        assertEquals(TicketReplyType.AI.name(), captor.getValue().getReplyType());
    }

    private Ticket ticket(Long id, Long userId, String status) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUserId(userId);
        ticket.setTitle("title");
        ticket.setContent("content");
        ticket.setStatus(status);
        ticket.setPriority("MEDIUM");
        ticket.setCategory("OTHER");
        return ticket;
    }
}

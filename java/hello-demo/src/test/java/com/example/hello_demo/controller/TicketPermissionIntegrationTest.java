package com.example.hello_demo.controller;

import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.TicketReply;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.service.AiPendingActionService;
import com.example.hello_demo.service.OperationLogService;
import com.example.hello_demo.service.TicketCacheService;
import com.example.hello_demo.service.TicketService;
import com.example.hello_demo.service.TicketStatusTransitionPolicy;
import com.example.hello_demo.vo.TicketDetailVO;
import com.example.hello_demo.vo.UserInfoVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketPermissionIntegrationTest extends MockMvcIntegrationTestSupport {

    private TicketMapper ticketMapper;
    private UserMapper userMapper;
    private TicketReplyMapper ticketReplyMapper;
    private OperationLogService operationLogService;
    private TicketCacheService ticketCacheService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        userMapper = mock(UserMapper.class);
        ticketReplyMapper = mock(TicketReplyMapper.class);
        operationLogService = mock(OperationLogService.class);
        ticketCacheService = mock(TicketCacheService.class);

        TicketService ticketService = new TicketService(
                ticketMapper,
                userMapper,
                ticketReplyMapper,
                operationLogService,
                ticketCacheService,
                new TicketStatusTransitionPolicy()
        );
        TicketController controller = new TicketController(
                ticketService,
                mock(AiPendingActionService.class),
                operationLogService
        );
        mockMvc = mockMvc(controller);
    }

    @Test
    void tomCannotViewOthersTicketDetail() throws Exception {
        when(ticketMapper.selectById(2L)).thenReturn(ticket(2L, 2L, "他人的工单", "他人的工单描述"));

        mockMvc.perform(get("/tickets/2/detail")
                        .header("Authorization", tomToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(content().string(not(containsString("他人的工单"))))
                .andExpect(content().string(not(containsString("他人的工单描述"))))
                .andExpect(content().string(not(containsString("他人的回复"))));

        verify(userMapper, never()).selectById(anyLong());
        verify(ticketReplyMapper, never()).selectList(any());
    }

    @Test
    void tomCannotUpdateTicketStatus() throws Exception {
        Ticket existing = ticket(1L, 1L, "tom 的工单", "tom 的工单描述");

        mockMvc.perform(put("/tickets/1/status")
                        .header("Authorization", tomToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(ticketMapper, never()).selectById(eq(1L));
        verify(ticketMapper, never()).updateById(any(Ticket.class));
        verify(operationLogService, never()).record(any(), any(), any(), any());
        verify(ticketCacheService, never()).evictTicketRelated(anyLong());
        org.junit.jupiter.api.Assertions.assertEquals("OPEN", existing.getStatus());
    }

    @Test
    void redisCacheHitDoesNotBypassPermission() throws Exception {
        Ticket cachedTicket = ticket(2L, 2L, "Redis 中他人的工单", "Redis 中他人的描述");
        TicketDetailVO cachedDetail = new TicketDetailVO(
                cachedTicket,
                new UserInfoVO(2L, "other", "Other User", null, "other@example.com", "USER"),
                List.of(reply(2L, "他人的回复内容"))
        );
        when(ticketCacheService.getTicketDetail(2L)).thenReturn(cachedDetail);

        mockMvc.perform(get("/tickets/2/detail")
                        .header("Authorization", tomToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(content().string(not(containsString("Redis 中他人的工单"))))
                .andExpect(content().string(not(containsString("Redis 中他人的描述"))))
                .andExpect(content().string(not(containsString("他人的回复内容"))));

        verify(ticketMapper, never()).selectById(anyLong());
        verify(userMapper, never()).selectById(anyLong());
        verify(ticketReplyMapper, never()).selectList(any());
    }

    private Ticket ticket(Long id, Long userId, String title, String content) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUserId(userId);
        ticket.setTitle(title);
        ticket.setContent(content);
        ticket.setStatus("OPEN");
        ticket.setPriority("MEDIUM");
        return ticket;
    }

    private TicketReply reply(Long ticketId, String content) {
        TicketReply reply = new TicketReply();
        reply.setId(20L + ticketId);
        reply.setTicketId(ticketId);
        reply.setContent(content);
        reply.setReplyType("STAFF");
        return reply;
    }
}

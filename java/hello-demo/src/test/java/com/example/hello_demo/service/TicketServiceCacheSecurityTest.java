package com.example.hello_demo.service;

import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.dto.TicketCreateDTO;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.vo.TicketDetailVO;
import com.example.hello_demo.vo.UserInfoVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceCacheSecurityTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void cachedDetailStillChecksUserPermission() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(2L, "other-user", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketDetail(1L)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void ownerCanReadCachedDetail() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "owner", "USER");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void staffCanReadCachedDetailForAnyTicket() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(3L, "staff", "STAFF");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void adminCanReadCachedDetailForAnyTicket() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(1L, 1L),
                new UserInfoVO(1L, "owner", "Owner", null, "owner@example.com", "USER"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(1L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(9L, "admin", "ADMIN");

        TicketDetailVO result = ticketService.getTicketDetail(1L);

        assertSame(cached, result);
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void normalUserCannotReadCachedAdminTicketDetail() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketDetailVO cached = new TicketDetailVO(
                ticket(2L, 9L),
                new UserInfoVO(9L, "admin", "Admin", null, "admin@example.com", "ADMIN"),
                List.of()
        );
        when(ticketCacheService.getTicketDetail(2L)).thenReturn(cached);

        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.getTicketDetail(2L)
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void normalUserCannotUpdateTicketStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.updateTicketStatus(1L, TicketStatus.PROCESSING.name())
        );

        assertEquals(403, exception.getCode());
        verify(ticketMapper, never()).selectById(anyLong());
    }

    @Test
    void staffCanUpdateTicketStatus() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        Ticket ticket = ticket(1L, 1L);
        when(ticketMapper.selectById(1L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(Ticket.class))).thenReturn(1);
        TicketService ticketService = ticketService(ticketMapper, ticketCacheService);
        CurrentUserContext.set(2L, "staff", "STAFF");

        Ticket result = ticketService.updateTicketStatus(1L, TicketStatus.PROCESSING.name());

        assertEquals(TicketStatus.PROCESSING.name(), result.getStatus());
        verify(ticketMapper).updateById(any(Ticket.class));
        verify(ticketCacheService).evictTicketRelated(1L);
    }

    @Test
    void createTicketUsesCurrentUserFromContext() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        TicketCacheService ticketCacheService = mock(TicketCacheService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AtomicReference<Ticket> inserted = new AtomicReference<>();
        when(userMapper.selectById(7L)).thenReturn(user(7L));
        doAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            ticket.setId(20L);
            inserted.set(ticket);
            return 1;
        }).when(ticketMapper).insert(any(Ticket.class));
        when(ticketMapper.selectById(20L)).thenAnswer(invocation -> inserted.get());
        TicketService ticketService = new TicketService(
                ticketMapper,
                userMapper,
                mock(TicketReplyMapper.class),
                operationLogService,
                ticketCacheService,
                new TicketStatusTransitionPolicy()
        );
        CurrentUserContext.set(7L, "tom", "USER");
        TicketCreateDTO dto = new TicketCreateDTO();
        dto.setTitle("Cannot login");
        dto.setContent("User cannot login with correct password");
        dto.setPriority("HIGH");

        Ticket created = ticketService.createTicket(dto);

        assertNotNull(created);
        assertEquals(7L, inserted.get().getUserId());
        assertEquals(TicketStatus.OPEN.name(), inserted.get().getStatus());
        verify(userMapper).selectById(7L);
        verify(operationLogService).record(
                eq("CREATE_TICKET"),
                eq("TICKET"),
                eq(20L),
                eq("用户创建了工单 #20")
        );
    }

    private TicketService ticketService(TicketMapper ticketMapper, TicketCacheService ticketCacheService) {
        return new TicketService(
                ticketMapper,
                mock(UserMapper.class),
                mock(TicketReplyMapper.class),
                mock(OperationLogService.class),
                ticketCacheService,
                new TicketStatusTransitionPolicy()
        );
    }

    private Ticket ticket(Long id, Long userId) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUserId(userId);
        ticket.setTitle("title");
        ticket.setContent("content");
        ticket.setStatus(TicketStatus.OPEN.name());
        ticket.setPriority("MEDIUM");
        ticket.setCategory("OTHER");
        return ticket;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setRole("USER");
        return user;
    }
}

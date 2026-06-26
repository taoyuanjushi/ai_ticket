package com.example.hello_demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.security.CurrentUserContext;
import com.example.hello_demo.security.PermissionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void userCannotReadTicketLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        TicketMapper ticketMapper = mock(TicketMapper.class);
        OperationLogService service = service(operationLogMapper, ticketMapper);
        when(ticketMapper.selectById(3L)).thenReturn(ticket(3L));
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getTicketLogs(3L, 1L, 20L)
        );

        assertEquals(403, exception.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void staffCanReadTicketLogsInCurrentProjectScope() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        TicketMapper ticketMapper = mock(TicketMapper.class);
        TicketReplyMapper ticketReplyMapper = mock(TicketReplyMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        OperationLogService service = new OperationLogService(operationLogMapper, ticketMapper, ticketReplyMapper, userMapper);

        when(ticketMapper.selectById(3L)).thenReturn(ticket(3L));
        when(ticketReplyMapper.selectList(any())).thenReturn(List.of());
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<OperationLog> page = invocation.getArgument(0);
            page.setRecords(List.of(log(7L, 2L, "UPDATE_TICKET_STATUS", "TICKET", 3L, "changed")));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(2L, "Staff One", "STAFF")));
        CurrentUserContext.set(2L, "staff", "STAFF");

        var result = service.getTicketLogs(3L, 1L, 20L);

        assertEquals(1, result.getTotal());
        assertEquals(3L, result.getRecords().get(0).getTicketId());
        assertEquals(2L, result.getRecords().get(0).getOperatorId());
        assertEquals("Staff One", result.getRecords().get(0).getOperatorName());
        assertEquals("UPDATE_TICKET_STATUS", result.getRecords().get(0).getAction());
        assertEquals("changed", result.getRecords().get(0).getDetail());
    }

    @Test
    void adminCanReadGlobalOperationLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        OperationLogService service = service(operationLogMapper, userMapper);
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<OperationLog> page = invocation.getArgument(0);
            page.setRecords(List.of(log(1L, 3L, "DELETE_TICKET", "TICKET", 9L, "deleted")));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(3L, "Admin One", "ADMIN")));
        CurrentUserContext.set(3L, "admin", "ADMIN");

        var result = service.getOperationLogs(1L, 10L, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals(9L, result.getRecords().get(0).getTicketId());
        assertEquals("DELETE_TICKET", result.getRecords().get(0).getAction());
    }

    @Test
    void userCannotReadGlobalOperationLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        OperationLogService service = service(operationLogMapper, mock(UserMapper.class));
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getOperationLogs(1L, 10L, null, null, null)
        );

        assertEquals(403, exception.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void ticketLogsReturn404WhenTicketDoesNotExist() {
        OperationLogService service = service(mock(OperationLogMapper.class), mock(TicketMapper.class));
        CurrentUserContext.set(3L, "admin", "ADMIN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getTicketLogs(404L, 1L, 20L)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void sizeGreaterThanMaxReturns400() {
        OperationLogService service = service(mock(OperationLogMapper.class), mock(UserMapper.class));
        CurrentUserContext.set(3L, "admin", "ADMIN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getOperationLogs(1L, 101L, null, null, null)
        );

        assertEquals(400, exception.getCode());
    }

    @Test
    void permissionHelpersFollowOperationLogRules() {
        Ticket ticket = ticket(8L);

        assertFalse(PermissionUtil.canViewTicketLogs(user(1L, "Tom", "USER"), ticket));
        assertTrue(PermissionUtil.canViewTicketLogs(user(2L, "Staff One", "STAFF"), ticket));
        assertTrue(PermissionUtil.canViewTicketLogs(user(3L, "Admin One", "ADMIN"), ticket));
        assertFalse(PermissionUtil.canViewGlobalOperationLogs(user(1L, "Tom", "USER")));
        assertTrue(PermissionUtil.canViewGlobalOperationLogs(user(2L, "Staff One", "STAFF")));
        assertTrue(PermissionUtil.canViewGlobalOperationLogs(user(3L, "Admin One", "ADMIN")));
    }

    private OperationLogService service(OperationLogMapper operationLogMapper, TicketMapper ticketMapper) {
        return new OperationLogService(operationLogMapper, ticketMapper, mock(TicketReplyMapper.class), mock(UserMapper.class));
    }

    private OperationLogService service(OperationLogMapper operationLogMapper, UserMapper userMapper) {
        return new OperationLogService(operationLogMapper, mock(TicketMapper.class), mock(TicketReplyMapper.class), userMapper);
    }

    private Ticket ticket(Long id) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setUserId(1L);
        ticket.setStatus("OPEN");
        return ticket;
    }

    private User user(Long id, String name, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(name.toLowerCase().replace(" ", ""));
        user.setName(name);
        user.setRole(role);
        return user;
    }

    private OperationLog log(Long id, Long userId, String operationType, String businessType, Long businessId, String content) {
        return new OperationLog(id, userId, operationType, businessType, businessId, content, LocalDateTime.now());
    }
}

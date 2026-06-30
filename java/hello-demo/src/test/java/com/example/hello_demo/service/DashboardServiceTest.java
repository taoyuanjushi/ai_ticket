package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @AfterEach
    void clearContext() {
        CurrentUserContext.clear();
    }

    @Test
    void adminGetsDatabaseCounts() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        DashboardService service = new DashboardService(ticketMapper, operationLogMapper, new SlaPolicy());

        when(ticketMapper.selectCount(any(Wrapper.class)))
                .thenReturn(12L, 4L, 3L, 0L, 5L, 2L, 1L, 6L, 2L);
        when(operationLogMapper.selectCount(any(Wrapper.class)))
                .thenReturn(8L, 5L);
        CurrentUserContext.set(9L, "admin", "ADMIN");

        var stats = service.getStats();

        assertEquals(12L, stats.ticketTotal());
        assertEquals(4L, stats.pendingCount());
        assertEquals(3L, stats.processingCount());
        assertEquals(0L, stats.doneCount());
        assertEquals(5L, stats.closedCount());
        assertEquals(2L, stats.highPriorityCount());
        assertEquals(1L, stats.urgentPriorityCount());
        assertEquals(6L, stats.slaAtRiskCount());
        assertEquals(2L, stats.slaOverdueCount());
        assertEquals(8L, stats.aiSuggestionCount());
        assertEquals(5L, stats.aiAcceptedCount());
        assertEquals(0.625, stats.aiAcceptanceRate());
    }

    @Test
    void zeroSuggestionCountReturnsZeroRate() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        DashboardService service = new DashboardService(ticketMapper, operationLogMapper, new SlaPolicy());

        when(ticketMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(operationLogMapper.selectCount(any(Wrapper.class))).thenReturn(0L, 0L);
        CurrentUserContext.set(9L, "admin", "ADMIN");

        assertEquals(0.0, service.getStats().aiAcceptanceRate());
    }

    @Test
    void userCannotGetStats() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        DashboardService service = new DashboardService(ticketMapper, operationLogMapper, new SlaPolicy());
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(BusinessException.class, service::getStats);

        assertEquals(403, exception.getCode());
        verifyNoInteractions(ticketMapper, operationLogMapper);
    }

    @Test
    void staffCannotGetStats() {
        TicketMapper ticketMapper = mock(TicketMapper.class);
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        DashboardService service = new DashboardService(ticketMapper, operationLogMapper, new SlaPolicy());
        CurrentUserContext.set(2L, "staff", "STAFF");

        BusinessException exception = assertThrows(BusinessException.class, service::getStats);

        assertEquals(403, exception.getCode());
        verifyNoInteractions(ticketMapper, operationLogMapper);
    }
}

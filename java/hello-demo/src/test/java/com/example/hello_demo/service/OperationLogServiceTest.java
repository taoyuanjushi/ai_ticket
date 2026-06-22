package com.example.hello_demo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.exception.BusinessException;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void normalUserCannotReadOperationLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        OperationLogService operationLogService = new OperationLogService(operationLogMapper);
        CurrentUserContext.set(1L, "tom", "USER");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> operationLogService.getOperationLogs(1L, 10L, null, null, null)
        );

        assertEquals(403, exception.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void staffCannotReadOperationLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        OperationLogService operationLogService = new OperationLogService(operationLogMapper);
        CurrentUserContext.set(2L, "staff", "STAFF");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> operationLogService.getOperationLogs(1L, 10L, null, null, null)
        );

        assertEquals(403, exception.getCode());
        verify(operationLogMapper, never()).selectPage(any(), any());
    }

    @Test
    void adminCanReadOperationLogs() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        OperationLogService operationLogService = new OperationLogService(operationLogMapper);
        Page<OperationLog> page = new Page<>(1, 10);
        page.setRecords(List.of(new OperationLog()));
        page.setTotal(1);
        when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(page);
        CurrentUserContext.set(3L, "admin", "ADMIN");

        var result = operationLogService.getOperationLogs(1L, 10L, null, null, null);

        assertEquals(1, result.getTotal());
        verify(operationLogMapper).selectPage(any(Page.class), any());
    }
}

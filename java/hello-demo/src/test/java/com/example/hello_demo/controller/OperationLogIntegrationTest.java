package com.example.hello_demo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.hello_demo.common.PageResult;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.entity.User;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.mapper.TicketReplyMapper;
import com.example.hello_demo.mapper.UserMapper;
import com.example.hello_demo.service.OperationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationLogIntegrationTest extends MockMvcIntegrationTestSupport {

    private OperationLogMapper operationLogMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        operationLogMapper = mock(OperationLogMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        OperationLogService service = new OperationLogService(
                operationLogMapper,
                mock(TicketMapper.class),
                mock(TicketReplyMapper.class),
                userMapper
        );
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(9L, "admin", "ADMIN")));
        mockMvc = mockMvc(new OperationLogController(service));
    }

    @Test
    void adminCanViewOperationLogs() throws Exception {
        when(operationLogMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<OperationLog> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    log(2L, 9L, "UPDATE_TICKET_STATUS", "TICKET", 3L, "newer operation token=secret-token"),
                    log(1L, 9L, "CREATE_TICKET", "TICKET", 1L, "older operation password=secret-password")
            ));
            page.setTotal(2);
            return page;
        });

        mockMvc.perform(get("/operation-logs?page=1&size=20")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(2))
                .andExpect(jsonPath("$.data.records[0].ticketId").value(3))
                .andExpect(jsonPath("$.data.records[0].action").value("UPDATE_TICKET_STATUS"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("secret-password"))))
                .andExpect(content().string(not(containsString("token=secret-token"))))
                .andExpect(content().string(not(containsString("password=secret-password"))));
    }

    @Test
    void userCannotViewOperationLogs() throws Exception {
        mockMvc.perform(get("/operation-logs?page=1&size=20")
                        .header("Authorization", tomToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verify(operationLogMapper, never()).selectPage(any(Page.class), any());
    }

    private OperationLog log(
            Long id,
            Long userId,
            String operationType,
            String businessType,
            Long businessId,
            String content) {
        return new OperationLog(
                id,
                userId,
                operationType,
                businessType,
                businessId,
                content,
                LocalDateTime.now().minusSeconds(id)
        );
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setName(username);
        user.setRole(role);
        return user;
    }
}

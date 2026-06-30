package com.example.hello_demo.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.service.DashboardService;
import com.example.hello_demo.service.SlaPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDashboardControllerTest extends MockMvcIntegrationTestSupport {

    private TicketMapper ticketMapper;
    private OperationLogMapper operationLogMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketMapper = mock(TicketMapper.class);
        operationLogMapper = mock(OperationLogMapper.class);
        DashboardService service = new DashboardService(ticketMapper, operationLogMapper, new SlaPolicy());
        mockMvc = mockMvc(new AdminDashboardController(service));
    }

    @Test
    void adminCanGetDashboardStats() throws Exception {
        when(ticketMapper.selectCount(any(Wrapper.class)))
                .thenReturn(12L, 4L, 3L, 0L, 5L, 2L, 1L, 6L, 2L);
        when(operationLogMapper.selectCount(any(Wrapper.class)))
                .thenReturn(8L, 5L);

        mockMvc.perform(get("/admin/dashboard/stats")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketTotal").value(12))
                .andExpect(jsonPath("$.data.pendingCount").value(4))
                .andExpect(jsonPath("$.data.slaAtRiskCount").value(6))
                .andExpect(jsonPath("$.data.slaOverdueCount").value(2))
                .andExpect(jsonPath("$.data.aiAcceptanceRate").value(0.625));
    }

    @Test
    void userCannotGetDashboardStats() throws Exception {
        mockMvc.perform(get("/admin/dashboard/stats")
                        .header("Authorization", tomToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(ticketMapper, operationLogMapper);
    }

    @Test
    void staffCannotGetDashboardStats() throws Exception {
        mockMvc.perform(get("/admin/dashboard/stats")
                        .header("Authorization", staffToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(ticketMapper, operationLogMapper);
    }
}

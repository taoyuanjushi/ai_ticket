package com.example.hello_demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.hello_demo.entity.OperationLog;
import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.enums.OperationType;
import com.example.hello_demo.mapper.OperationLogMapper;
import com.example.hello_demo.mapper.TicketMapper;
import com.example.hello_demo.security.PermissionUtil;
import com.example.hello_demo.vo.DashboardStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DashboardService {

    private final TicketMapper ticketMapper;
    private final OperationLogMapper operationLogMapper;
    private final SlaPolicy slaPolicy;

    public DashboardService(TicketMapper ticketMapper, OperationLogMapper operationLogMapper, SlaPolicy slaPolicy) {
        this.ticketMapper = ticketMapper;
        this.operationLogMapper = operationLogMapper;
        this.slaPolicy = slaPolicy;
    }

    @Transactional(readOnly = true)
    public DashboardStatsVO getStats() {
        PermissionUtil.requireAdmin();

        Long ticketTotal = countTickets();
        Long pendingCount = countTicketsByStatuses("OPEN", "PENDING");
        Long processingCount = countTicketsByStatus("PROCESSING");
        Long doneCount = countTicketsByStatus("DONE");
        Long closedCount = countTicketsByStatus("CLOSED");
        Long highPriorityCount = countTicketsByPriority("HIGH");
        Long urgentPriorityCount = countTicketsByPriority("URGENT");
        LocalDateTime now = LocalDateTime.now();
        Long slaAtRiskCount = countSlaAtRisk(now);
        Long slaOverdueCount = countSlaOverdue(now);
        Long aiSuggestionCount = countOperation(OperationType.AI_REPLY_SUGGESTION);
        Long aiAcceptedCount = countOperation(OperationType.AI_REPLY_CONFIRMED);

        double aiAcceptanceRate = aiSuggestionCount == 0
                ? 0.0
                : aiAcceptedCount.doubleValue() / aiSuggestionCount.doubleValue();

        return new DashboardStatsVO(
                ticketTotal,
                pendingCount,
                processingCount,
                doneCount,
                closedCount,
                highPriorityCount,
                urgentPriorityCount,
                slaAtRiskCount,
                slaOverdueCount,
                aiSuggestionCount,
                aiAcceptedCount,
                aiAcceptanceRate
        );
    }

    private Long countTickets() {
        return safeCount(ticketMapper.selectCount(new QueryWrapper<Ticket>()));
    }

    private Long countTicketsByStatus(String status) {
        return safeCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getStatus, status)
        ));
    }

    private Long countTicketsByStatuses(String... statuses) {
        return safeCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().in(Ticket::getStatus, List.of(statuses))
        ));
    }

    private Long countTicketsByPriority(String priority) {
        return safeCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getPriority, priority)
        ));
    }

    private Long countSlaAtRisk(LocalDateTime now) {
        return safeCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .notIn(Ticket::getStatus, List.of("CLOSED", "DONE"))
                        .isNotNull(Ticket::getResolveDueAt)
                        .ge(Ticket::getResolveDueAt, now)
                        .le(Ticket::getResolveDueAt, slaPolicy.riskThreshold(now))
        ));
    }

    private Long countSlaOverdue(LocalDateTime now) {
        return safeCount(ticketMapper.selectCount(
                new LambdaQueryWrapper<Ticket>()
                        .notIn(Ticket::getStatus, List.of("CLOSED", "DONE"))
                        .isNotNull(Ticket::getResolveDueAt)
                        .lt(Ticket::getResolveDueAt, now)
        ));
    }

    private Long countOperation(OperationType operationType) {
        return safeCount(operationLogMapper.selectCount(
                new LambdaQueryWrapper<OperationLog>().eq(OperationLog::getOperationType, operationType.name())
        ));
    }

    private Long safeCount(Long value) {
        return value == null ? 0L : value;
    }
}

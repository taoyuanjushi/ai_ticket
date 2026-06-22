package com.example.hello_demo.service;

import com.example.hello_demo.enums.TicketStatus;
import com.example.hello_demo.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 工单状态流转规则。
 * 所有状态修改入口都应通过这里校验，避免规则散落在业务代码中。
 */
@Component
public class TicketStatusTransitionPolicy {

    public TicketStatus normalize(String status) {
        TicketStatus ticketStatus = TicketStatus.from(status);
        if (ticketStatus == null) {
            throw new BusinessException(400, "工单状态不合法");
        }
        return ticketStatus;
    }

    public boolean canTransit(TicketStatus from, TicketStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        if (from == TicketStatus.OPEN) {
            return to == TicketStatus.PROCESSING || to == TicketStatus.CLOSED;
        }
        if (from == TicketStatus.PROCESSING) {
            return to == TicketStatus.CLOSED;
        }
        return false;
    }

    public void validateTransition(String fromStatus, String toStatus) {
        TicketStatus from = normalize(fromStatus);
        TicketStatus to = normalize(toStatus);
        validateTransition(from, to);
    }

    public void validateTransition(TicketStatus from, TicketStatus to) {
        if (!canTransit(from, to)) {
            throw new BusinessException(
                    400,
                    "工单状态不允许从 " + from.name() + " 修改为 " + to.name()
            );
        }
    }
}

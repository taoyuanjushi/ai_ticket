package com.example.hello_demo.service;

import com.example.hello_demo.entity.Ticket;
import com.example.hello_demo.enums.SlaStatus;
import com.example.hello_demo.enums.TicketStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class SlaPolicy {

    public static final Duration RISK_WINDOW = Duration.ofHours(4);

    public LocalDateTime calculateResponseDueAt(String priority, LocalDateTime createdAt) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.plus(responseDuration(priority));
    }

    public LocalDateTime calculateResolveDueAt(String priority, LocalDateTime createdAt) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.plus(resolveDuration(priority));
    }

    public LocalDateTime riskThreshold(LocalDateTime now) {
        return now.plus(RISK_WINDOW);
    }

    public void applySlaSnapshot(Ticket ticket) {
        applySlaSnapshot(ticket, LocalDateTime.now());
    }

    public void applySlaSnapshot(Ticket ticket, LocalDateTime now) {
        if (ticket == null) {
            return;
        }

        SlaStatus status = statusFor(ticket, now);
        ticket.setSlaStatus(status);
        ticket.setSlaOverdue(status == SlaStatus.OVERDUE);
        if (status == SlaStatus.NO_SLA) {
            ticket.setSlaRemainingMinutes(null);
        } else if (status == SlaStatus.COMPLETED) {
            ticket.setSlaRemainingMinutes(0L);
        } else {
            ticket.setSlaRemainingMinutes(Duration.between(now, ticket.getResolveDueAt()).toMinutes());
        }
    }

    public SlaStatus statusFor(Ticket ticket, LocalDateTime now) {
        if (ticket == null) {
            return SlaStatus.NO_SLA;
        }
        if (isCompletedStatus(ticket.getStatus())) {
            return SlaStatus.COMPLETED;
        }
        if (ticket.getResolveDueAt() == null) {
            return SlaStatus.NO_SLA;
        }
        if (now.isAfter(ticket.getResolveDueAt())) {
            return SlaStatus.OVERDUE;
        }
        long remainingMinutes = Duration.between(now, ticket.getResolveDueAt()).toMinutes();
        if (remainingMinutes <= RISK_WINDOW.toMinutes()) {
            return SlaStatus.AT_RISK;
        }
        return SlaStatus.ON_TRACK;
    }

    public boolean isCompletedStatus(String status) {
        return TicketStatus.CLOSED.name().equalsIgnoreCase(status);
    }

    private Duration responseDuration(String priority) {
        return switch (normalizePriority(priority)) {
            case "URGENT" -> Duration.ofMinutes(30);
            case "HIGH" -> Duration.ofHours(2);
            case "LOW" -> Duration.ofHours(24);
            default -> Duration.ofHours(8);
        };
    }

    private Duration resolveDuration(String priority) {
        return switch (normalizePriority(priority)) {
            case "URGENT" -> Duration.ofHours(4);
            case "HIGH" -> Duration.ofHours(24);
            case "LOW" -> Duration.ofDays(7);
            default -> Duration.ofHours(72);
        };
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        return priority.trim().toUpperCase();
    }
}

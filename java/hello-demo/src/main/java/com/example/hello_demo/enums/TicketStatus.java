package com.example.hello_demo.enums;

/**
 * 工单状态枚举。
 * 用于限制工单只能使用固定状态，避免出现非法状态值。
 */
public enum TicketStatus {

    OPEN,
    PROCESSING,
    CLOSED;

    /**
     * 判断传入状态是否是合法工单状态。
     */
    public static boolean isValid(String status) {
        return from(status) != null;
    }

    public static TicketStatus from(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }

        String normalizedStatus = status.trim().toUpperCase();
        for (TicketStatus ticketStatus : TicketStatus.values()) {
            if (ticketStatus.name().equals(normalizedStatus)) {
                return ticketStatus;
            }
        }

        return null;
    }
}

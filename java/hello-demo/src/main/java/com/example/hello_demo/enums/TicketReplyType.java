package com.example.hello_demo.enums;

/**
 * 工单回复类型枚举。
 * USER 表示普通用户回复，STAFF 表示客服/管理员回复，AI 表示后续 AI 回复建议。
 */
public enum TicketReplyType {

    USER,
    STAFF,
    AI;

    public static boolean isValid(String replyType) {
        if (replyType == null || replyType.trim().isEmpty()) {
            return false;
        }

        for (TicketReplyType type : TicketReplyType.values()) {
            if (type.name().equals(replyType)) {
                return true;
            }
        }

        return false;
    }
}

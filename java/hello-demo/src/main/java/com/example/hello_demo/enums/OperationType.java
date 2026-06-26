package com.example.hello_demo.enums;

/**
 * 操作类型枚举。
 */
public enum OperationType {

    CREATE_TICKET,
    REPLY_TICKET,
    UPDATE_TICKET_STATUS,
    TICKET_CATEGORY_UPDATED,
    TICKET_ASSIGNEE_UPDATED,
    DELETE_TICKET,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    REGISTER_USER,
    AI_TICKET_QUERY,
    AI_REPLY_SUGGESTED,
    AI_PENDING_ACTION_CREATED,
    AI_WRITE_CONFIRMED,
    AI_WRITE_CANCELLED,
    AI_REPLY_CREATED,
    AI_REPLY_SAVE_PENDING_CREATED,
    AI_REPLY_SAVED,
    AI_CATEGORY_APPLY_PENDING_CREATED,
    AI_CATEGORY_APPLIED,
    AI_ACTION_CANCELLED,
    AI_ACTION_CONFIRM_FAILED;

    public static boolean isValid(String operationType) {
        return from(operationType) != null;
    }

    public static String normalize(String operationType) {
        OperationType type = from(operationType);
        return type == null ? null : type.name();
    }

    private static OperationType from(String operationType) {
        if (operationType == null || operationType.trim().isEmpty()) {
            return null;
        }

        String normalizedType = operationType.trim().toUpperCase();
        for (OperationType type : OperationType.values()) {
            if (type.name().equals(normalizedType)) {
                return type;
            }
        }

        return null;
    }
}

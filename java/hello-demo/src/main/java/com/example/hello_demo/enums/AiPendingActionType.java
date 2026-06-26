package com.example.hello_demo.enums;

/**
 * AI 待确认动作类型。
 */
public enum AiPendingActionType {
    CREATE_TICKET,
    UPDATE_TICKET_STATUS,
    SAVE_AI_REPLY,
    APPLY_AI_CATEGORY;

    public static AiPendingActionType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalizedValue = value.trim().toUpperCase();
        for (AiPendingActionType type : values()) {
            if (type.name().equals(normalizedValue)) {
                return type;
            }
        }
        return null;
    }
}

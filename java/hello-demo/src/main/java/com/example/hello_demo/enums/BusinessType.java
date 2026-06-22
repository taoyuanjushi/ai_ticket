package com.example.hello_demo.enums;

/**
 * 业务类型枚举。
 */
public enum BusinessType {

    AUTH,
    USER,
    TICKET,
    TICKET_REPLY,
    AI_PENDING_ACTION;

    public static boolean isValid(String businessType) {
        return from(businessType) != null;
    }

    public static String normalize(String businessType) {
        BusinessType type = from(businessType);
        return type == null ? null : type.name();
    }

    private static BusinessType from(String businessType) {
        if (businessType == null || businessType.trim().isEmpty()) {
            return null;
        }

        String normalizedType = businessType.trim().toUpperCase();
        for (BusinessType type : BusinessType.values()) {
            if (type.name().equals(normalizedType)) {
                return type;
            }
        }

        return null;
    }
}

package com.example.hello_demo.enums;

/**
 * 用户角色枚举。
 * USER 表示普通用户，STAFF 表示客服人员，ADMIN 表示管理员。
 */
public enum UserRole {

    USER,
    STAFF,
    ADMIN;

    public static boolean isValid(String role) {
        return from(role) != null;
    }

    public static boolean isAdmin(String role) {
        return ADMIN == from(role);
    }

    public static boolean isStaff(String role) {
        return STAFF == from(role);
    }

    public static boolean isUser(String role) {
        return USER == from(role);
    }

    public static boolean isStaffOrAdmin(String role) {
        UserRole userRole = from(role);
        return STAFF == userRole || ADMIN == userRole;
    }

    private static UserRole from(String role) {
        if (role == null || role.trim().isEmpty()) {
            return null;
        }

        String normalizedRole = role.trim().toUpperCase();
        for (UserRole userRole : UserRole.values()) {
            if (userRole.name().equals(normalizedRole)) {
                return userRole;
            }
        }

        return null;
    }
}

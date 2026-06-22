package com.example.hello_demo.security;

import com.example.hello_demo.enums.UserRole;
import com.example.hello_demo.exception.BusinessException;

/**
 * 权限校验工具类。
 * 用于在 Service 层判断当前用户是否有权限执行某个操作。
 */
public class PermissionUtil {

    private static final String LOGIN_EXPIRED_MESSAGE = "登录状态已失效，请重新登录。";
    private static final String FORBIDDEN_MESSAGE = "你没有权限执行该操作。";

    private PermissionUtil() {
    }

    public static Long requireLoginUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, LOGIN_EXPIRED_MESSAGE);
        }
        return userId;
    }

    public static String requireLoginRole() {
        String role = CurrentUserContext.getRole();
        if (role == null || role.trim().isEmpty()) {
            throw new BusinessException(401, LOGIN_EXPIRED_MESSAGE);
        }
        if (!UserRole.isValid(role)) {
            throw new BusinessException(403, FORBIDDEN_MESSAGE);
        }
        return role.trim().toUpperCase();
    }

    public static void requireAdmin() {
        String role = requireLoginRole();
        if (!UserRole.isAdmin(role)) {
            throw new BusinessException(403, FORBIDDEN_MESSAGE);
        }
    }

    public static void requireStaffOrAdmin() {
        String role = requireLoginRole();
        if (!UserRole.isStaffOrAdmin(role)) {
            throw new BusinessException(403, FORBIDDEN_MESSAGE);
        }
    }

    public static boolean isAdmin() {
        return UserRole.isAdmin(CurrentUserContext.getRole());
    }

    public static boolean isStaff() {
        return UserRole.isStaff(CurrentUserContext.getRole());
    }

    public static boolean isUser() {
        return UserRole.isUser(CurrentUserContext.getRole());
    }

    public static boolean isStaffOrAdmin() {
        return UserRole.isStaffOrAdmin(CurrentUserContext.getRole());
    }
}

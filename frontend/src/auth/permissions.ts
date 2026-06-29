import type { CurrentUser, UserRole } from "../types/domain";

export function normalizeRole(role?: string | null): UserRole {
  const normalized = role?.trim().toUpperCase();
  if (normalized === "ROLE_ADMIN" || normalized === "ADMIN") return "ADMIN";
  if (normalized === "ROLE_STAFF" || normalized === "STAFF") return "STAFF";
  return "USER";
}

export function normalizeCurrentUser<T extends Partial<CurrentUser> | null | undefined>(
  user: T,
): T extends null | undefined ? null : CurrentUser {
  if (!user) {
    return null as T extends null | undefined ? null : CurrentUser;
  }
  return {
    id: Number(user.id ?? 0),
    username: String(user.username ?? ""),
    name: String(user.name ?? user.username ?? ""),
    age: user.age,
    email: String(user.email ?? ""),
    role: normalizeRole(user.role),
  } as T extends null | undefined ? null : CurrentUser;
}

export function isStaff(role?: UserRole | string | null): boolean {
  const normalized = normalizeRole(role);
  return normalized === "STAFF" || normalized === "ADMIN";
}

export function isAdmin(role?: UserRole | string | null): boolean {
  return normalizeRole(role) === "ADMIN";
}

export function canCreateTicket(role?: UserRole | string | null): boolean {
  return ["USER", "STAFF", "ADMIN"].includes(normalizeRole(role));
}

export function canUpdateTicketStatus(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canEditTicket(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canDeleteTicket(role?: UserRole | string | null): boolean {
  return isAdmin(role);
}

export function canViewOperationLogs(role?: UserRole | string | null): boolean {
  return isAdmin(role);
}

export function canViewTicketLogs(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canSaveAiReply(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canApplyCategory(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canAssignTicket(role?: UserRole | string | null): boolean {
  return isStaff(role);
}

export function canViewAdminArea(role?: UserRole | string | null): boolean {
  return isAdmin(role);
}

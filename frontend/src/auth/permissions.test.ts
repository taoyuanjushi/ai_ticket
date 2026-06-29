import { describe, expect, it } from "vitest";
import {
  canApplyCategory,
  canAssignTicket,
  canCreateTicket,
  canSaveAiReply,
  canUpdateTicketStatus,
  canViewAdminArea,
  canViewOperationLogs,
  canViewTicketLogs,
  normalizeRole,
} from "./permissions";

describe("permissions", () => {
  it("normalizes backend role values", () => {
    expect(normalizeRole("ROLE_USER")).toBe("USER");
    expect(normalizeRole("ROLE_STAFF")).toBe("STAFF");
    expect(normalizeRole("ROLE_ADMIN")).toBe("ADMIN");
    expect(normalizeRole("ADMIN")).toBe("ADMIN");
    expect(normalizeRole("unknown")).toBe("USER");
    expect(normalizeRole(undefined)).toBe("USER");
  });

  it("keeps USER on customer-facing capabilities only", () => {
    expect(canCreateTicket("USER")).toBe(true);
    expect(canUpdateTicketStatus("USER")).toBe(false);
    expect(canViewOperationLogs("USER")).toBe(false);
    expect(canViewTicketLogs("USER")).toBe(false);
    expect(canSaveAiReply("USER")).toBe(false);
    expect(canApplyCategory("USER")).toBe(false);
    expect(canAssignTicket("USER")).toBe(false);
    expect(canViewAdminArea("USER")).toBe(false);
  });

  it("allows STAFF to process tickets without admin-only areas", () => {
    expect(canCreateTicket("STAFF")).toBe(true);
    expect(canUpdateTicketStatus("STAFF")).toBe(true);
    expect(canSaveAiReply("STAFF")).toBe(true);
    expect(canApplyCategory("STAFF")).toBe(true);
    expect(canAssignTicket("STAFF")).toBe(true);
    expect(canViewOperationLogs("STAFF")).toBe(false);
    expect(canViewTicketLogs("STAFF")).toBe(true);
    expect(canViewAdminArea("STAFF")).toBe(false);
  });

  it("allows ADMIN to use staff capabilities and admin-only areas", () => {
    expect(canCreateTicket("ADMIN")).toBe(true);
    expect(canUpdateTicketStatus("ADMIN")).toBe(true);
    expect(canSaveAiReply("ADMIN")).toBe(true);
    expect(canApplyCategory("ADMIN")).toBe(true);
    expect(canAssignTicket("ADMIN")).toBe(true);
    expect(canViewOperationLogs("ADMIN")).toBe(true);
    expect(canViewTicketLogs("ADMIN")).toBe(true);
    expect(canViewAdminArea("ADMIN")).toBe(true);
  });
});

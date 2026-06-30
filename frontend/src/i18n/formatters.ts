import type { TicketPriority, TicketSlaStatus, TicketStatus } from "../types/domain";
import type { TFunction } from "./I18nProvider";

export function formatTicketStatus(status: TicketStatus | string, t: TFunction) {
  return t(`status.${status}`, status);
}

export function formatPriority(priority: TicketPriority | string, t: TFunction) {
  return t(`priority.${priority}`, priority);
}

export function formatReplyType(type: string, t: TFunction) {
  return t(`replyType.${type}`, type);
}

export function formatSlaStatus(status: TicketSlaStatus | string, t: TFunction) {
  return t(`slaStatus.${status}`, status);
}

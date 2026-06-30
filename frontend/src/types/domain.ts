export type UserRole = "USER" | "STAFF" | "ADMIN";
export type TicketStatus = "OPEN" | "PROCESSING" | "CLOSED";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type TicketSlaStatus = "NO_SLA" | "ON_TRACK" | "AT_RISK" | "OVERDUE" | "COMPLETED";
export type ReplyType = "USER" | "STAFF" | "AI";
export type BusinessType = "AUTH" | "USER" | "TICKET" | "TICKET_REPLY" | "AI_PENDING_ACTION";

export const operationTypeOptions = [
  "CREATE_TICKET",
  "REPLY_TICKET",
  "UPDATE_TICKET_STATUS",
  "TICKET_CATEGORY_UPDATED",
  "TICKET_ASSIGNEE_UPDATED",
  "DELETE_TICKET",
  "LOGIN_SUCCESS",
  "LOGIN_FAILED",
  "REGISTER_USER",
  "AI_QUERY_TICKET",
  "AI_CREATE_TICKET_PENDING",
  "AI_CREATE_TICKET_CONFIRMED",
  "AI_CREATE_TICKET_CANCELLED",
  "AI_UPDATE_STATUS_PENDING",
  "AI_UPDATE_STATUS_CONFIRMED",
  "AI_UPDATE_STATUS_CANCELLED",
  "AI_REPLY_SUGGESTION",
  "AI_REPLY_PENDING",
  "AI_REPLY_CONFIRMED",
  "AI_REPLY_CANCELLED",
  "AI_CLASSIFY_TICKET",
  "AI_ERROR",
  "AI_FORBIDDEN",
  "AI_TICKET_QUERY",
  "AI_REPLY_SUGGESTED",
  "AI_PENDING_ACTION_CREATED",
  "AI_WRITE_CONFIRMED",
  "AI_WRITE_CANCELLED",
  "AI_REPLY_CREATED",
  "AI_REPLY_SAVE_PENDING_CREATED",
  "AI_REPLY_SAVED",
  "AI_CATEGORY_APPLY_PENDING_CREATED",
  "AI_CATEGORY_APPLIED",
  "AI_ACTION_CANCELLED",
  "AI_ACTION_CONFIRM_FAILED",
] as const;

export type OperationType = (typeof operationTypeOptions)[number];

export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface DashboardStats {
  ticketTotal?: number | null;
  pendingCount?: number | null;
  processingCount?: number | null;
  doneCount?: number | null;
  closedCount?: number | null;
  highPriorityCount?: number | null;
  urgentPriorityCount?: number | null;
  aiSuggestionCount?: number | null;
  aiAcceptedCount?: number | null;
  aiAcceptanceRate?: number | null;
  slaAtRiskCount?: number | null;
  slaOverdueCount?: number | null;
}

export interface CurrentUser {
  id: number;
  username: string;
  name: string;
  age?: number;
  email: string;
  role: UserRole;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  role: UserRole;
}

export interface User extends CurrentUser {
  password?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface Ticket {
  id: number;
  title: string;
  content: string;
  status: TicketStatus;
  priority: TicketPriority;
  category?: string | null;
  assignedTo?: number | null;
  assignedUserName?: string | null;
  userId: number;
  responseDueAt?: string | null;
  resolveDueAt?: string | null;
  closedAt?: string | null;
  slaStatus?: TicketSlaStatus | null;
  slaOverdue?: boolean | null;
  slaRemainingMinutes?: number | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface TicketReply {
  id: number;
  ticketId: number;
  userId: number;
  authorName?: string | null;
  authorRole?: UserRole | string | null;
  content: string;
  replyType: ReplyType;
  createdAt?: string;
  updatedAt?: string;
}

export interface TicketDetail {
  ticket: Ticket;
  user: CurrentUser;
  replies: TicketReply[];
}

export interface TicketQuery {
  page?: number;
  size?: number;
  status?: TicketStatus | "";
  priority?: TicketPriority | "";
  category?: string;
  keyword?: string;
  assignedTo?: string;
}

export interface TicketCreateInput {
  title: string;
  content: string;
  priority?: TicketPriority;
  category?: string;
}

export interface TicketUpdateInput {
  title: string;
  content: string;
  priority: TicketPriority;
  status: TicketStatus;
  category?: string;
}

export interface OperationLog {
  id: number;
  ticketId?: number | null;
  operatorId?: number | null;
  operatorName?: string | null;
  username?: string | null;
  role?: UserRole | string | null;
  action: OperationType | string;
  detail: string;
  operationSource?: "MANUAL" | "AI" | string | null;
  actionType?: OperationType | string | null;
  conversationId?: string | null;
  targetType?: BusinessType | string | null;
  targetId?: number | null;
  resultStatus?: "SUCCESS" | "FAILED" | "CANCELLED" | "FORBIDDEN" | string | null;
  requestSummary?: string | null;
  resultSummary?: string | null;
  createdAt?: string;
}

export interface AiChatResponse {
  answer?: unknown;
  type?: string;
  message?: string;
  data?: unknown;
  risk_flags?: string[];
  [key: string]: unknown;
}

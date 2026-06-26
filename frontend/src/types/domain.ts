export type UserRole = "USER" | "STAFF" | "ADMIN";
export type TicketStatus = "OPEN" | "PROCESSING" | "CLOSED";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type ReplyType = "USER" | "STAFF" | "AI";
export type BusinessType = "AUTH" | "USER" | "TICKET" | "TICKET_REPLY";
export type OperationType =
  | "CREATE_TICKET"
  | "REPLY_TICKET"
  | "UPDATE_TICKET_STATUS"
  | "TICKET_CATEGORY_UPDATED"
  | "TICKET_ASSIGNEE_UPDATED"
  | "DELETE_TICKET"
  | "LOGIN_SUCCESS"
  | "LOGIN_FAILED"
  | "REGISTER_USER"
  | "AI_TICKET_QUERY"
  | "AI_REPLY_SUGGESTED"
  | "AI_PENDING_ACTION_CREATED"
  | "AI_WRITE_CONFIRMED"
  | "AI_WRITE_CANCELLED"
  | "AI_REPLY_CREATED";

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
  createdAt?: string;
  updatedAt?: string;
}

export interface TicketReply {
  id: number;
  ticketId: number;
  userId: number;
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
  action: OperationType | string;
  detail: string;
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

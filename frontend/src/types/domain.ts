export type UserRole = "USER" | "STAFF" | "ADMIN";
export type TicketStatus = "OPEN" | "PROCESSING" | "CLOSED";
export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type ReplyType = "USER" | "STAFF" | "AI";
export type BusinessType = "AUTH" | "USER" | "TICKET" | "TICKET_REPLY";
export type OperationType =
  | "CREATE_TICKET"
  | "REPLY_TICKET"
  | "UPDATE_TICKET_STATUS"
  | "DELETE_TICKET"
  | "LOGIN_SUCCESS"
  | "LOGIN_FAILED"
  | "REGISTER_USER";

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
  category: string;
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
  userId: number;
  operationType: OperationType;
  businessType: BusinessType;
  businessId: number;
  content: string;
  createdAt?: string;
}

export interface AiChatResponse {
  answer?: unknown;
  [key: string]: unknown;
}

export interface ReplySuggestionResponse {
  ticket_id?: number;
  suggestion?: string;
  confidence?: number;
  reason?: string;
  risk_flags?: string[];
}

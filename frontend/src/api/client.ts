import { request } from "./http";
import type {
  AiChatResponse,
  CurrentUser,
  DashboardStats,
  LoginResponse,
  OperationLog,
  PageResult,
  Ticket,
  TicketCreateInput,
  TicketDetail,
  TicketPriority,
  TicketQuery,
  TicketReply,
  TicketStatus,
  TicketUpdateInput,
  User,
  UserRole,
} from "../types/domain";

export const api = {
  login: (body: { username: string; password: string }) =>
    request<LoginResponse>("/auth/login", { method: "POST", body }),

  register: (body: { username: string; password: string; name: string; age: number; email: string }) =>
    request<CurrentUser>("/auth/register", { method: "POST", body }),

  me: () => request<CurrentUser>("/auth/me"),

  getTickets: (query: TicketQuery) => request<PageResult<Ticket>>("/tickets", { query: { ...query } }),

  getTicketDetail: (id: number) => request<TicketDetail>(`/tickets/${id}/detail`),

  createTicket: (body: TicketCreateInput) => request<Ticket>("/tickets", { method: "POST", body }),

  updateTicket: (id: number, body: TicketUpdateInput) =>
    request<boolean>(`/tickets/${id}`, { method: "PUT", body }),

  updateTicketStatus: (id: number, status: TicketStatus) =>
    request<Ticket>(`/tickets/${id}/status`, { method: "PUT", body: { status } }),

  updateTicketAssignee: (id: number, assignedTo: number | null) =>
    request<Ticket>(`/tickets/${id}/assignee`, { method: "PATCH", body: { assignedTo } }),

  deleteTicket: (id: number) => request<boolean>(`/tickets/${id}`, { method: "DELETE" }),

  replyTicket: (ticketId: number, content: string) =>
    request<TicketReply>(`/tickets/${ticketId}/replies`, { method: "POST", body: { content } }),

  getUsers: () => request<User[]>("/users"),

  createUser: (body: {
    username: string;
    password: string;
    name: string;
    age: number;
    email: string;
    role: UserRole;
  }) => request<User>("/users", { method: "POST", body }),

  updateUser: (
    id: number,
    body: {
      username?: string;
      password?: string;
      name: string;
      age: number;
      email: string;
      role?: UserRole;
    },
  ) => request<User>(`/users/${id}`, { method: "PUT", body }),

  deleteUser: (id: number) => request<void>(`/users/${id}`, { method: "DELETE" }),

  getUserTickets: (userId: number) => request<Ticket[]>(`/users/${userId}/tickets`),

  getUserReplies: (userId: number) => request<TicketReply[]>(`/users/${userId}/ticket-replies`),

  getOperationLogs: (query: {
    page?: number;
    size?: number;
    ticketId?: number | string;
    operatorId?: number | string;
    action?: string;
    operationSource?: string;
    actionType?: string;
    resultStatus?: string;
    conversationId?: string;
  }) => request<PageResult<OperationLog>>("/operation-logs", { query }),

  getTicketLogs: (ticketId: number, query: { page?: number; size?: number }) =>
    request<PageResult<OperationLog>>(`/tickets/${ticketId}/logs`, { query }),

  getDashboardStats: () => request<DashboardStats>("/admin/dashboard/stats"),

  aiChat: (message: string, conversationId: string) =>
    request<AiChatResponse>("/ai/chat", { method: "POST", body: { message, conversationId } }),

  createAiReplyPending: (ticketId: number, conversationId: string, content: string) =>
    request<AiChatResponse>(`/tickets/${ticketId}/ai-replies/pending`, {
      method: "POST",
      body: { conversationId, content },
    }),

  createCategoryPending: (
    ticketId: number,
    body: {
      conversationId: string;
      category: string;
      confidence?: number;
      reason?: string;
    },
  ) => request<AiChatResponse>(`/tickets/${ticketId}/category/pending`, { method: "POST", body }),
};

export const priorityOptions: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];
export const statusOptions: TicketStatus[] = ["OPEN", "PROCESSING", "CLOSED"];
export const roleOptions: UserRole[] = ["USER", "STAFF", "ADMIN"];

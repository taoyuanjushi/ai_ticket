import type {
  AiChatResponse,
  CurrentUser,
  LoginResponse,
  OperationLog,
  PageResult,
  ReplySuggestionResponse,
  Ticket,
  TicketDetail,
  TicketPriority,
  TicketReply,
  TicketStatus,
  User,
} from "../types/domain";

type MockOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
};

let activeUserId = 2;
let ticketIdSeq = 7;
let replyIdSeq = 8;

const users: User[] = [
  { id: 1, username: "alice", name: "Alice Chen", age: 28, email: "alice@example.com", role: "USER" },
  { id: 2, username: "staff01", name: "Staff One", age: 31, email: "staff01@example.com", role: "STAFF" },
  { id: 3, username: "admin01", name: "Admin One", age: 35, email: "admin01@example.com", role: "ADMIN" },
];

let tickets: Ticket[] = [
  makeTicket(1, "Login failure", "User cannot sign in even with the correct password.", "OPEN", "HIGH", "ACCOUNT", 1, -75),
  makeTicket(2, "Slow dashboard", "The support dashboard takes more than eight seconds to load.", "PROCESSING", "MEDIUM", "SYSTEM", 1, -180),
  makeTicket(3, "Invoice data not saved", "Invoice fields disappear after refresh.", "OPEN", "URGENT", "BILLING", 1, -45),
  makeTicket(4, "Delayed email notifications", "Status change emails are delayed.", "CLOSED", "LOW", "NOTIFICATION", 1, -480),
  makeTicket(5, "Mobile layout issue", "The reply box overlaps the submit button on narrow screens.", "PROCESSING", "HIGH", "FRONTEND", 1, -260),
  makeTicket(6, "Permission question", "User cannot see reply history and needs policy confirmation.", "OPEN", "MEDIUM", "PERMISSION", 1, -30),
];

let replies: TicketReply[] = [
  makeReply(1, 1, 1, "I have not been able to sign in since last night.", "USER", -70),
  makeReply(2, 1, 2, "Please provide a screenshot and approximate failure time.", "STAFF", -65),
  makeReply(3, 2, 1, "The list keeps spinning before it appears.", "USER", -170),
  makeReply(4, 2, 2, "Moved to processing. Initial check suggests a slow list query.", "STAFF", -120),
  makeReply(5, 3, 1, "This blocks reimbursement, please prioritize it.", "USER", -40),
  makeReply(6, 4, 2, "Mail service recovered. We will keep monitoring.", "STAFF", -440),
  makeReply(7, 5, 2, "The mobile layout issue has been reproduced.", "STAFF", -220),
];

const logs: OperationLog[] = [
  makeLog(1, 2, "LOGIN_SUCCESS", "AUTH", 2, "Staff login succeeded", -20),
  makeLog(2, 1, "CREATE_TICKET", "TICKET", 6, "User created ticket #6", -30),
  makeLog(3, 2, "UPDATE_TICKET_STATUS", "TICKET", 2, "Staff changed ticket #2 to PROCESSING", -120),
  makeLog(4, 2, "REPLY_TICKET", "TICKET_REPLY", 7, "Staff replied to ticket #5", -220),
];

export async function mockFetch<T>(path: string, options: MockOptions = {}): Promise<T> {
  await delay(180);
  const method = options.method ?? "GET";
  const route = normalizePath(path);

  if (route === "/auth/login" && method === "POST") {
    const body = options.body as { username?: string };
    const user =
      users.find((item) => item.username === body.username) ??
      users.find((item) => item.role === "STAFF") ??
      users[0];
    activeUserId = user.id;
    return {
      token: `mock-token-${user.role.toLowerCase()}`,
      userId: user.id,
      username: user.username,
      role: user.role,
    } as LoginResponse as T;
  }

  if (route === "/auth/register" && method === "POST") {
    const body = options.body as Partial<User>;
    const user: User = {
      id: nextId(users),
      username: body.username ?? `user${users.length + 1}`,
      name: body.name ?? "New User",
      age: body.age ?? 20,
      email: body.email ?? "new@example.com",
      role: "USER",
    };
    users.push(user);
    activeUserId = user.id;
    return toCurrentUser(user) as T;
  }

  if (route === "/auth/me" && method === "GET") {
    return toCurrentUser(currentUser()) as T;
  }

  if (route === "/tickets" && method === "GET") {
    return listTickets(options.query ?? {}) as T;
  }

  if (route === "/tickets" && method === "POST") {
    const body = options.body as {
      title: string;
      content: string;
      priority?: TicketPriority;
      category?: string;
    };
    const ticket = makeTicket(ticketIdSeq++, body.title, body.content, "OPEN", body.priority ?? "MEDIUM", body.category || "OTHER", currentUser().id, 0);
    tickets = [ticket, ...tickets];
    logs.unshift(makeLog(nextId(logs), currentUser().id, "CREATE_TICKET", "TICKET", ticket.id, `User created ticket #${ticket.id}`, 0));
    return ticket as T;
  }

  const ticketDetailMatch = route.match(/^\/tickets\/(\d+)\/detail$/);
  if (ticketDetailMatch && method === "GET") {
    return getTicketDetail(Number(ticketDetailMatch[1])) as T;
  }

  const ticketStatusMatch = route.match(/^\/tickets\/(\d+)\/status$/);
  if (ticketStatusMatch && method === "PUT") {
    const ticket = findTicket(Number(ticketStatusMatch[1]));
    const body = options.body as { status: TicketStatus };
    ticket.status = body.status;
    ticket.updatedAt = now();
    logs.unshift(makeLog(nextId(logs), currentUser().id, "UPDATE_TICKET_STATUS", "TICKET", ticket.id, `Ticket #${ticket.id} changed to ${body.status}`, 0));
    return ticket as T;
  }

  const ticketMatch = route.match(/^\/tickets\/(\d+)$/);
  if (ticketMatch && method === "PUT") {
    const ticket = findTicket(Number(ticketMatch[1]));
    Object.assign(ticket, options.body, { updatedAt: now() });
    return true as T;
  }

  if (ticketMatch && method === "DELETE") {
    const id = Number(ticketMatch[1]);
    tickets = tickets.filter((ticket) => ticket.id !== id);
    replies = replies.filter((reply) => reply.ticketId !== id);
    logs.unshift(makeLog(nextId(logs), currentUser().id, "DELETE_TICKET", "TICKET", id, `Admin deleted ticket #${id}`, 0));
    return true as T;
  }

  const repliesMatch = route.match(/^\/tickets\/(\d+)\/replies$/);
  if (repliesMatch && method === "POST") {
    const ticketId = Number(repliesMatch[1]);
    const body = options.body as { content: string };
    const user = currentUser();
    const reply = makeReply(replyIdSeq++, ticketId, user.id, body.content, user.role === "USER" ? "USER" : "STAFF", 0);
    replies.push(reply);
    logs.unshift(makeLog(nextId(logs), user.id, "REPLY_TICKET", "TICKET_REPLY", reply.id, `User replied to ticket #${ticketId}`, 0));
    return reply as T;
  }

  if (repliesMatch && method === "GET") {
    return replies.filter((reply) => reply.ticketId === Number(repliesMatch[1])) as T;
  }

  if (route === "/users" && method === "GET") {
    return users as T;
  }

  if (route === "/users" && method === "POST") {
    const body = options.body as User;
    const user: User = { ...body, id: nextId(users) };
    users.push(user);
    return user as T;
  }

  const userMatch = route.match(/^\/users\/(\d+)$/);
  if (userMatch && method === "PUT") {
    const user = users.find((item) => item.id === Number(userMatch[1]));
    if (!user) throw new Error("User not found");
    Object.assign(user, options.body);
    return user as T;
  }

  if (userMatch && method === "DELETE") {
    const id = Number(userMatch[1]);
    const index = users.findIndex((item) => item.id === id);
    if (index >= 0) users.splice(index, 1);
    return undefined as T;
  }

  const userTicketsMatch = route.match(/^\/users\/(\d+)\/tickets$/);
  if (userTicketsMatch && method === "GET") {
    return tickets.filter((ticket) => ticket.userId === Number(userTicketsMatch[1])) as T;
  }

  const userRepliesMatch = route.match(/^\/users\/(\d+)\/ticket-replies$/);
  if (userRepliesMatch && method === "GET") {
    return replies.filter((reply) => reply.userId === Number(userRepliesMatch[1])) as T;
  }

  if (route === "/operation-logs" && method === "GET") {
    return listLogs(options.query ?? {}) as T;
  }

  if (route === "/ai/chat" && method === "POST") {
    const body = options.body as { message: string };
    const answer = buildMockAiChatAnswer(body.message);
    return {
      answer,
    } as AiChatResponse as T;
  }

  const suggestionMatch = route.match(/^\/ai\/tickets\/(\d+)\/reply-suggestion$/);
  if (suggestionMatch && method === "POST") {
    const ticket = findTicket(Number(suggestionMatch[1]));
    return {
      ticket_id: ticket.id,
      suggestion: `Confirm whether "${ticket.title}" is still reproducible, ask for timestamp, screenshot, and steps, then move the ticket into processing if the details are sufficient.`,
      confidence: 0.82,
      reason: "Generated from ticket title, content, status, and reply history.",
      risk_flags: ["需要人工确认"],
    } as ReplySuggestionResponse as T;
  }

  throw new Error(`Mock route not implemented: ${method} ${route}`);
}

function normalizePath(path: string) {
  return path.replace(/^\/api/, "");
}

function currentUser() {
  return users.find((user) => user.id === activeUserId) ?? users[0];
}

function toCurrentUser(user: User): CurrentUser {
  return {
    id: user.id,
    username: user.username,
    name: user.name,
    age: user.age,
    email: user.email,
    role: user.role,
  };
}

function listTickets(query: Record<string, string | number | undefined | null>): PageResult<Ticket> {
  const page = Number(query.page ?? 1);
  const size = Number(query.size ?? 10);
  const status = query.status ? String(query.status) : "";
  const priority = query.priority ? String(query.priority) : "";
  const category = query.category ? String(query.category).toUpperCase() : "";
  const keyword = query.keyword ? String(query.keyword).toLowerCase() : "";
  const user = currentUser();

  let result = [...tickets];
  if (user.role === "USER") {
    result = result.filter((ticket) => ticket.userId === user.id);
  }
  if (status) result = result.filter((ticket) => ticket.status === status);
  if (priority) result = result.filter((ticket) => ticket.priority === priority);
  if (category) result = result.filter((ticket) => ticket.category.toUpperCase() === category);
  if (keyword) {
    result = result.filter((ticket) => ticket.title.toLowerCase().includes(keyword) || ticket.content.toLowerCase().includes(keyword));
  }

  const start = (page - 1) * size;
  return { records: result.slice(start, start + size), total: result.length, page, size };
}

function listLogs(query: Record<string, string | number | undefined | null>): PageResult<OperationLog> {
  const page = Number(query.page ?? 1);
  const size = Number(query.size ?? 10);
  let result = [...logs];
  if (query.userId) result = result.filter((log) => log.userId === Number(query.userId));
  if (query.operationType) result = result.filter((log) => log.operationType === query.operationType);
  if (query.businessType) result = result.filter((log) => log.businessType === query.businessType);
  const start = (page - 1) * size;
  return { records: result.slice(start, start + size), total: result.length, page, size };
}

function getTicketDetail(id: number): TicketDetail {
  const ticket = findTicket(id);
  const user = users.find((item) => item.id === ticket.userId) ?? users[0];
  return {
    ticket,
    user: toCurrentUser(user),
    replies: replies.filter((reply) => reply.ticketId === id).sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt))),
  };
}

function findTicket(id: number) {
  const ticket = tickets.find((item) => item.id === id);
  if (!ticket) throw new Error("Ticket not found");
  return ticket;
}

function buildMockAiChatAnswer(message: string) {
  const ticketId = extractTicketId(message);
  if (message.includes("总结")) {
    return JSON.stringify({
      summary: `${ticketId} 号工单当前需要客服继续跟进。`,
      key_points: ["状态来自当前工单详情", "建议结合历史回复继续处理"],
      risk_flags: ["需要人工确认"],
    });
  }
  if (message.includes("优先级")) {
    return JSON.stringify({
      suggested_priority: "HIGH",
      confidence: 0.76,
      reason: "工单描述中存在阻塞或高影响关键词。",
      risk_flags: ["需要人工确认"],
    });
  }
  if (message.includes("分类")) {
    return JSON.stringify({
      suggested_category: "账号登录",
      confidence: 0.81,
      reason: "标题或描述中出现登录、账号、权限等关键词。",
      risk_flags: [],
    });
  }
  if (message.includes("相似") || message.includes("类似")) {
    return JSON.stringify({
      similar_tickets: [
        {
          id: 1,
          title: "Login failure",
          status: "OPEN",
          similarity_reason: "都涉及登录失败问题。",
        },
      ],
      risk_flags: [],
    });
  }
  if (message.toLowerCase().includes("sla")) {
    return JSON.stringify({
      sla_risk_level: "MEDIUM",
      reason: "高优先级工单仍未关闭，可能需要尽快处理。",
      missing_fields: ["resolveDueAt"],
      risk_flags: ["SLA字段不足"],
    });
  }
  if (message === "确认") {
    return "已执行当前会话中的待确认操作。";
  }
  if (message === "取消") {
    return "已取消当前会话中的待确认操作。";
  }
  return `Received: "${message}". I can help query tickets, create tickets, change status, and draft replies.`;
}

function extractTicketId(message: string) {
  const match = message.match(/(\d+)\s*号工单/);
  return match?.[1] ?? "当前";
}

function makeTicket(id: number, title: string, content: string, status: TicketStatus, priority: TicketPriority, category: string, userId: number, offsetMinutes: number): Ticket {
  const timestamp = time(offsetMinutes);
  return { id, title, content, status, priority, category, userId, createdAt: timestamp, updatedAt: timestamp };
}

function makeReply(id: number, ticketId: number, userId: number, content: string, replyType: "USER" | "STAFF" | "AI", offsetMinutes: number): TicketReply {
  const timestamp = time(offsetMinutes);
  return { id, ticketId, userId, content, replyType, createdAt: timestamp, updatedAt: timestamp };
}

function makeLog(id: number, userId: number, operationType: OperationLog["operationType"], businessType: OperationLog["businessType"], businessId: number, content: string, offsetMinutes: number): OperationLog {
  return { id, userId, operationType, businessType, businessId, content, createdAt: time(offsetMinutes) };
}

function nextId(items: Array<{ id: number }>) {
  return Math.max(0, ...items.map((item) => item.id)) + 1;
}

function now() {
  return new Date().toISOString();
}

function time(offsetMinutes: number) {
  return new Date(Date.now() + offsetMinutes * 60_000).toISOString();
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

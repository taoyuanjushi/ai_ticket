import type { ApiError } from "../api/http";
import type { AiMessage, AiMessageKind } from "../types/ai";

const jsonResultKeys = [
  "suggestion",
  "summary",
  "suggested_priority",
  "suggested_category",
  "similar_tickets",
  "sla_risk_level",
] as const;

const sensitiveKeys = new Set([
  "token",
  "authToken",
  "auth_token",
  "authorization",
  "Authorization",
  "userId",
  "user_id",
]);

export function createUserMessage(content: string): AiMessage {
  return {
    id: createMessageId("user"),
    role: "user",
    content,
    kind: "normal",
    createdAt: new Date().toISOString(),
  };
}

export function createAssistantMessage(response: unknown, fallback = ""): AiMessage {
  const content = extractMessageText(response) || fallback || stringifyContent(response);
  return {
    id: createMessageId("assistant"),
    role: "assistant",
    content,
    kind: detectAiMessageKind(response),
    raw: sanitizeAiRaw(response),
    createdAt: new Date().toISOString(),
  };
}

export function detectAiMessageKind(response: unknown): AiMessageKind {
  const status = extractStatus(response);
  if (status === 403) return "forbidden";
  if (status >= 500) return "error";

  const candidates = collectCandidates(response);
  const records = candidates.filter(isRecord);
  const normalizedTypes = records
    .map((item) => readString(item.type)?.toUpperCase())
    .filter(Boolean);

  if (
    normalizedTypes.includes("PENDING_CONFIRMATION") ||
    normalizedTypes.includes("PENDING") ||
    records.some((item) => item.requires_confirmation === true || item.confirm_required === true)
  ) {
    return "pending";
  }

  if (normalizedTypes.includes("FORBIDDEN")) return "forbidden";
  if (normalizedTypes.includes("ERROR")) return "error";
  if (normalizedTypes.includes("JSON_RESULT")) return "json";

  if (records.some((item) => isKnownAiJson(item) || (isRecord(item.data) && isKnownAiJson(item.data)))) {
    return "json";
  }

  const text = collectText(candidates).join("\n");
  const normalizedText = text.toLowerCase();

  if (
    text.includes("请确认") ||
    text.includes("是否确认") ||
    text.includes("待确认") ||
    normalizedText.includes("pending_action") ||
    normalizedText.includes("requires_confirmation") ||
    normalizedText.includes("confirm_required") ||
    normalizedText.includes("confirm")
  ) {
    return "pending";
  }

  if (
    text.includes("没有权限") ||
    text.includes("无权访问") ||
    text.includes("你没有权限执行该操作") ||
    text.includes("目标工单不存在，或你无权访问该工单")
  ) {
    return "forbidden";
  }

  if (
    normalizedText.includes("error") ||
    text.includes("失败") ||
    text.includes("服务暂时异常") ||
    text.includes("无法连接") ||
    text.includes("请稍后重试") ||
    text.includes("AI 分析失败") ||
    text.includes("JSON解析失败")
  ) {
    return "error";
  }

  return "normal";
}

export function extractStructuredPayload(response: unknown): Record<string, unknown> | null {
  const candidates = collectCandidates(response);

  for (const candidate of candidates) {
    if (!isRecord(candidate)) continue;
    if (isRecord(candidate.data) && isKnownAiJson(candidate.data)) {
      return mergeRiskFlags(candidate.data, candidate.risk_flags);
    }
    if (isKnownAiJson(candidate)) {
      return candidate;
    }
  }

  return null;
}

export function extractPendingAction(response: unknown) {
  const candidates = collectCandidates(response);
  for (const candidate of candidates) {
    if (!isRecord(candidate)) continue;
    const data = isRecord(candidate.data) ? candidate.data : candidate;
    const actionType =
      readString(data.actionType) ||
      readString(data.action_type) ||
      readString(data.type) ||
      readString(candidate.actionType);
    const payload = isRecord(data.payload) ? data.payload : undefined;
    const message = readString(candidate.message) || readString(data.message) || extractMessageText(response);
    const riskFlags = toStringList(candidate.risk_flags).length > 0
      ? toStringList(candidate.risk_flags)
      : toStringList(data.risk_flags);
    return {
      actionType,
      payload: payload ? sanitizeRecord(payload) : undefined,
      message,
      riskFlags,
    };
  }

  return {
    actionType: undefined,
    payload: undefined,
    message: extractMessageText(response),
    riskFlags: [],
  };
}

export function extractMessageText(response: unknown): string {
  if (typeof response === "string") {
    const parsed = parseJsonLike(response);
    if (parsed !== response) {
      return extractMessageText(parsed);
    }
    return response;
  }

  if (response instanceof Error) {
    return response.message;
  }

  if (!isRecord(response)) {
    return stringifyContent(response);
  }

  const message = readString(response.message);
  if (message) return message;

  const answer = response.answer;
  const parsedAnswer = parseJsonLike(answer);
  if (isRecord(parsedAnswer)) {
    const nestedMessage = readString(parsedAnswer.message);
    if (nestedMessage) return nestedMessage;
    if ("data" in parsedAnswer) return stringifyContent(parsedAnswer.data);
  }

  if (typeof answer === "string") return answer;
  if ("data" in response && response.data !== null && response.data !== undefined) {
    return stringifyContent(response.data);
  }

  return stringifyContent(response);
}

export function parseJsonLike(value: unknown): unknown {
  if (typeof value !== "string") {
    return value;
  }
  const text = value.trim();
  if (!text) {
    return value;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return value;
  }
}

export function isKnownAiJson(payload: Record<string, unknown>) {
  return jsonResultKeys.some((key) => key in payload);
}

export function toStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => stringifyScalar(item)).filter((item): item is string => Boolean(item));
}

export function stringifyScalar(value: unknown): string | null {
  if (value === null || value === undefined) return null;
  if (typeof value === "string") return value.trim() || null;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return null;
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

function collectCandidates(value: unknown): unknown[] {
  const parsed = parseJsonLike(value);
  const candidates: unknown[] = [parsed];

  if (isRecord(parsed)) {
    candidates.push(parsed.data);
    candidates.push(parsed.answer);
    candidates.push(parseJsonLike(parsed.answer));
    if (isRecord(parseJsonLike(parsed.answer))) {
      candidates.push((parseJsonLike(parsed.answer) as Record<string, unknown>).data);
    }
  }

  return candidates.filter((item) => item !== undefined && item !== null);
}

function collectText(values: unknown[]): string[] {
  return values.flatMap((value) => {
    if (typeof value === "string") return [value];
    if (value instanceof Error) return [value.message];
    if (isRecord(value)) {
      return [value.message, value.answer, value.data].flatMap((item) => collectText([item]));
    }
    return [];
  });
}

function extractStatus(value: unknown) {
  if (isRecord(value)) {
    const status = Number(value.status ?? value.code);
    return Number.isFinite(status) ? status : 0;
  }
  const maybeError = value as Partial<ApiError>;
  const status = Number(maybeError?.status ?? maybeError?.code);
  return Number.isFinite(status) ? status : 0;
}

function sanitizeAiRaw(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizeAiRaw(item));
  }
  if (!isRecord(value)) {
    return value;
  }
  return sanitizeRecord(value);
}

function sanitizeRecord(value: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(value)
      .filter(([key]) => !sensitiveKeys.has(key))
      .map(([key, entry]) => [key, sanitizeAiRaw(entry)]),
  );
}

function mergeRiskFlags(data: Record<string, unknown>, outerRiskFlags: unknown) {
  if ("risk_flags" in data) {
    return data;
  }
  const riskFlags = toStringList(outerRiskFlags);
  return riskFlags.length > 0 ? { ...data, risk_flags: riskFlags } : data;
}

function readString(value: unknown) {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function stringifyContent(value: unknown) {
  if (typeof value === "string") return value;
  if (value === null || value === undefined) return "";
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return JSON.stringify(value);
}

function createMessageId(prefix: string) {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return `${prefix}-${crypto.randomUUID()}`;
  }
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

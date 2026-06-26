import { useAuthStore } from "../state/authStore";
import type { Result } from "../types/domain";
import { mockFetch } from "./mock";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
const isNodeTestRuntime =
  typeof process !== "undefined" && process.env.NODE_ENV === "test";
const mockEnabled =
  import.meta.env.VITE_MOCK_API === "true" ||
  import.meta.env.MODE === "test" ||
  isNodeTestRuntime;

export class ApiError extends Error {
  code: number;
  status: number;

  constructor(message: string, code = 500, status = 500) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  query?: Record<string, string | number | undefined | null>;
}

export function isMockApiEnabled() {
  return mockEnabled;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (mockEnabled) {
    return mockFetch<T>(path, options);
  }

  const url = buildUrl(`${apiBaseUrl}${path}`, options.query);
  const token = useAuthStore.getState().token;

  const headers: HeadersInit = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(url, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  const payload = await parseJson(response);

  if (!response.ok) {
    const message = extractMessage(payload) || response.statusText;
    if (clearAuthSessionIfExpired(response.status, message)) {
      throw new ApiError("登录状态已失效，请重新登录。", 401, 401);
    }
    throw new ApiError(message, response.status, response.status);
  }

  if (isJavaResult<T>(payload)) {
    if (clearAuthSessionIfExpired(payload.code, payload.message)) {
      throw new ApiError("登录状态已失效，请重新登录。", 401, 401);
    }
    if (payload.code !== 200) {
      throw new ApiError(payload.message, payload.code, response.status);
    }
    return payload.data;
  }

  return payload as T;
}

function buildUrl(path: string, query?: RequestOptions["query"]) {
  const url = new URL(path, window.location.origin);
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      url.searchParams.set(key, String(value));
    }
  });
  return url.toString();
}

async function parseJson(response: Response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function extractMessage(payload: unknown) {
  if (payload && typeof payload === "object" && "message" in payload) {
    return String((payload as { message: unknown }).message);
  }
  return "";
}

export function clearAuthSessionIfExpired(status: number, message: string) {
  if (status === 401 || isAuthExpiredMessage(message)) {
    useAuthStore.getState().clearSession();
    return true;
  }
  return false;
}

export function isAuthExpiredMessage(message: string) {
  const normalized = message.toLowerCase();
  return (
    message.includes("Token格式错误") ||
    message.includes("登录状态已失效") ||
    message.includes("请先登录") ||
    message.includes("请重新登录") ||
    normalized.includes("token invalid") ||
    normalized.includes("token expired")
  );
}

function isJavaResult<T>(payload: unknown): payload is Result<T> {
  return Boolean(
    payload &&
      typeof payload === "object" &&
      "code" in payload &&
      "message" in payload &&
      "data" in payload,
  );
}

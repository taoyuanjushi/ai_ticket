export type AiMessageKind = "normal" | "pending" | "forbidden" | "error" | "json";

export type AiMessageRole = "user" | "assistant";

export interface AiMessage {
  id: string;
  role: AiMessageRole;
  content: string;
  kind: AiMessageKind;
  raw?: unknown;
  createdAt: string;
}

export type AiLoadingMode = "thinking" | "confirm" | "cancel" | null;

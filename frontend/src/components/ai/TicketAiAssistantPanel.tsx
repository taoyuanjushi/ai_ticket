import { useQueryClient } from "@tanstack/react-query";
import { Clock, Sparkles, Trash2 } from "lucide-react";
import { useState } from "react";
import { api } from "../../api/client";
import { canApplyCategory, canSaveAiReply, normalizeRole } from "../../auth/permissions";
import { Button } from "../Button";
import { EmptyState } from "../EmptyState";
import { AiErrorCard } from "./AiErrorCard";
import { AiJsonResultCard } from "./AiJsonResultCard";
import { AiLoadingIndicator } from "./AiLoadingIndicator";
import { PendingActionCard } from "./PendingActionCard";
import { useI18n } from "../../i18n";
import { useAuthStore } from "../../state/authStore";
import type { AiLoadingMode, AiMessageKind } from "../../types/ai";
import { createAssistantMessage, stringifyScalar, toStringList } from "../../utils/aiMessages";

interface TicketAiAssistantPanelProps {
  ticketId: number;
}

interface TicketAiAction {
  key: string;
  labelKey: string;
  buildMessage: (ticketId: number) => string;
}

interface TicketAiResultItem {
  id: string;
  actionKey: string;
  actionLabel: string;
  message: string;
  response: unknown;
  kind: AiMessageKind;
  content: string;
  createdAt: string;
}

const actions: TicketAiAction[] = [
  {
    key: "replySuggestion",
    labelKey: "ticketAi.replySuggestion",
    buildMessage: (ticketId) => `给 ${ticketId} 号工单生成回复建议`,
  },
  {
    key: "summary",
    labelKey: "ticketAi.summary",
    buildMessage: (ticketId) => `总结 ${ticketId} 号工单`,
  },
  {
    key: "priority",
    labelKey: "ticketAi.priority",
    buildMessage: (ticketId) => `判断 ${ticketId} 号工单优先级`,
  },
  {
    key: "category",
    labelKey: "ticketAi.category",
    buildMessage: (ticketId) => `判断 ${ticketId} 号工单属于什么分类`,
  },
  {
    key: "similar",
    labelKey: "ticketAi.similar",
    buildMessage: (ticketId) => `查询 ${ticketId} 号工单相似工单`,
  },
  {
    key: "sla",
    labelKey: "ticketAi.sla",
    buildMessage: (ticketId) => `检查 ${ticketId} 号工单 SLA 风险`,
  },
];

export function TicketAiAssistantPanel({ ticketId }: TicketAiAssistantPanelProps) {
  const { lang, t } = useI18n();
  const queryClient = useQueryClient();
  const role = normalizeRole(useAuthStore((state) => state.user?.role));
  const [conversationId] = useState(() => createConversationId());
  const [results, setResults] = useState<TicketAiResultItem[]>([]);
  const [loadingAction, setLoadingAction] = useState<string | null>(null);

  const busy = loadingAction !== null;
  const maySaveAiReply = canSaveAiReply(role);
  const mayApplyCategory = canApplyCategory(role);

  const sendAiRequest = async (
    actionKey: string,
    actionLabel: string,
    message: string,
    options: { onSuccess?: () => void | Promise<void> } = {},
  ) => {
    if (loadingAction) return;
    setLoadingAction(actionKey);

    try {
      const response = await api.aiChat(message, conversationId);
      appendResult(actionKey, actionLabel, message, response);
      await options.onSuccess?.();
    } catch (error) {
      appendResult(actionKey, actionLabel, message, error, t("ticketAi.requestFailed"));
    } finally {
      setLoadingAction(null);
    }
  };

  const sendPendingRequest = async (
    actionKey: string,
    actionLabel: string,
    message: string,
    requestFactory: () => Promise<unknown>,
  ) => {
    if (loadingAction) return;
    setLoadingAction(actionKey);
    try {
      const response = await requestFactory();
      appendResult(actionKey, actionLabel, message, response);
    } catch (error) {
      appendResult(actionKey, actionLabel, message, error, t("ticketAi.requestFailed"));
    } finally {
      setLoadingAction(null);
    }
  };

  const appendResult = (
    key: string,
    label: string,
    prompt: string,
    response: unknown,
    fallback = "",
  ) => {
    const assistant = createAssistantMessage(response, fallback);
    setResults((prev) => [
      {
        id: assistant.id,
        actionKey: key,
        actionLabel: label,
        message: prompt,
        response: assistant.raw ?? response,
        kind: assistant.kind,
        content: assistant.content,
        createdAt: assistant.createdAt,
      },
      ...prev,
    ]);
  };

  const runAction = (action: TicketAiAction) => {
    void sendAiRequest(
      action.key,
      t(action.labelKey),
      action.buildMessage(ticketId),
    );
  };

  const runConfirmOrCancel = (
    source: TicketAiResultItem,
    command: "确认" | "取消",
    mode: "confirm" | "cancel",
  ) => {
    void sendAiRequest(
      `${source.id}:${mode}`,
      mode === "confirm" ? t("ai.confirmAction") : t("ai.cancelAction"),
      command,
      {
        onSuccess: async () => {
          await refreshAfterAiAction();
          clearAiDrafts();
        },
      },
    );
  };

  const requestSaveAiReply = (source: TicketAiResultItem, payload: Record<string, unknown>) => {
    const suggestion = stringifyScalar(payload.suggestion);
    if (!suggestion) return;
    const originalSuggestion = stringifyScalar(payload.originalSuggestion) ?? suggestion;
    const confidence = typeof payload.confidence === "number" ? payload.confidence : undefined;
    const reason = stringifyScalar(payload.reason) ?? undefined;
    const riskFlags = toStringList(payload.riskFlags).length > 0
      ? toStringList(payload.riskFlags)
      : toStringList(payload.risk_flags);
    const message = `保存 ${ticketId} 号工单的 AI 回复建议`;
    void sendPendingRequest(
      `${source.id}:save-ai-reply`,
      t("actions.saveAiReply"),
      message,
      () =>
        api.createAiReplyPending(ticketId, {
          conversationId,
          suggestion,
          originalSuggestion,
          confidence,
          reason,
          riskFlags,
        }),
    );
  };

  const requestApplyCategory = (source: TicketAiResultItem, payload: Record<string, unknown>) => {
    const category = stringifyScalar(payload.suggested_category);
    if (!category) return;
    const confidence = typeof payload.confidence === "number" ? payload.confidence : undefined;
    const reason = stringifyScalar(payload.reason) ?? undefined;
    const message = `采纳 ${ticketId} 号工单的 AI 分类建议：${category}`;
    void sendPendingRequest(
      `${source.id}:apply-category`,
      t("actions.applyCategory"),
      message,
      () =>
        api.createCategoryPending(ticketId, {
          conversationId,
          category,
          confidence,
          reason,
        }),
    );
  };

  const refreshAfterAiAction = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["ticket-detail", ticketId] }),
      queryClient.invalidateQueries({ queryKey: ["ticket-logs", ticketId] }),
      queryClient.invalidateQueries({ queryKey: ["tickets"] }),
      queryClient.invalidateQueries({ queryKey: ["logs"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-dashboard-stats"] }),
    ]);
  };

  const clearAiDrafts = () => {
    setResults((prev) => prev.filter((item) => item.kind !== "json" && item.kind !== "pending"));
  };

  return (
    <section className="rounded border border-line bg-white p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <Sparkles className="h-4 w-4 text-brand" aria-hidden="true" />
          <div>
            <h2 className="text-base font-semibold">{t("ticketAi.title")}</h2>
            <p className="mt-1 text-sm text-muted">{t("ticketAi.description")}</p>
          </div>
        </div>
        {results.length > 0 ? (
          <Button variant="ghost" onClick={() => setResults([])} disabled={busy}>
            <Trash2 className="h-4 w-4" aria-hidden="true" />
            {t("ticketAi.clear")}
          </Button>
        ) : null}
      </div>

      <div className="mt-4 grid grid-cols-2 gap-2">
        {actions.map((action) => (
          <Button
            key={action.key}
            variant="secondary"
            onClick={() => runAction(action)}
            disabled={busy}
            className="justify-start"
          >
            {loadingAction === action.key ? t("ticketAi.processing") : t(action.labelKey)}
          </Button>
        ))}
      </div>

      <div className="mt-4">
        <AiLoadingIndicator mode={loadingModeFromAction(loadingAction)} />
      </div>

      <div className="mt-4 grid gap-3">
        {results.length === 0 ? (
          <EmptyState title={t("ticketAi.noResult")} text={t("ticketAi.noResultText")} />
        ) : (
          results.map((item) => (
            <article key={item.id} className="rounded border border-line bg-panel p-4">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold text-ink">{item.actionLabel}</p>
                  <p className="mt-1 break-words text-xs text-muted">{item.message}</p>
                </div>
                <span className="inline-flex items-center gap-1 text-xs text-muted">
                  <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                  {formatTime(item.createdAt, lang)}
                </span>
              </div>
              <TicketAiResultCard
                item={item}
                busyMode={loadingModeForResult(loadingAction, item.id)}
                onRetry={() =>
                  void sendAiRequest(
                    `${item.id}:retry`,
                    item.actionLabel,
                    item.message,
                  )
                }
                onConfirm={() => runConfirmOrCancel(item, "确认", "confirm")}
                onCancel={() => runConfirmOrCancel(item, "取消", "cancel")}
                canSaveAiReply={maySaveAiReply}
                canApplyCategory={mayApplyCategory}
                onSaveAiReply={(payload) => requestSaveAiReply(item, payload)}
                onApplyCategory={(payload) => requestApplyCategory(item, payload)}
              />
            </article>
          ))
        )}
      </div>
    </section>
  );
}

function TicketAiResultCard({
  item,
  busyMode,
  onRetry,
  onConfirm,
  onCancel,
  canSaveAiReply,
  canApplyCategory,
  onSaveAiReply,
  onApplyCategory,
}: {
  item: TicketAiResultItem;
  busyMode: AiLoadingMode;
  onRetry: () => void;
  onConfirm: () => void;
  onCancel: () => void;
  canSaveAiReply: boolean;
  canApplyCategory: boolean;
  onSaveAiReply: (payload: Record<string, unknown>) => void;
  onApplyCategory: (payload: Record<string, unknown>) => void;
}) {
  if (item.kind === "pending") {
    return (
      <PendingActionCard
        response={item.response}
        busyMode={busyMode}
        onConfirm={onConfirm}
        onCancel={onCancel}
      />
    );
  }

  if (item.kind === "json") {
    return (
      <AiJsonResultCard
        response={item.response}
        canSaveAiReply={canSaveAiReply}
        canApplyCategory={canApplyCategory}
        onSaveAiReply={onSaveAiReply}
        onApplyCategory={onApplyCategory}
      />
    );
  }

  if (item.kind === "error" || item.kind === "forbidden") {
    return (
      <AiErrorCard
        kind={item.kind}
        message={item.content}
        canRetry={item.kind === "error"}
        onRetry={onRetry}
      />
    );
  }

  return (
    <div className="rounded border border-blue-100 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-900">
      <p className="whitespace-pre-wrap">{item.content}</p>
    </div>
  );
}

function loadingModeFromAction(action: string | null): AiLoadingMode {
  if (!action) return null;
  if (action.endsWith(":confirm")) return "confirm";
  if (action.endsWith(":cancel")) return "cancel";
  return "thinking";
}

function loadingModeForResult(action: string | null, resultId: string): AiLoadingMode {
  if (action === `${resultId}:confirm`) return "confirm";
  if (action === `${resultId}:cancel`) return "cancel";
  return null;
}

function createConversationId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `ticket-ai-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function formatTime(value: string, lang: string) {
  return new Date(value).toLocaleTimeString(lang === "zh" ? "zh-CN" : "en-US", {
    hour12: false,
  });
}

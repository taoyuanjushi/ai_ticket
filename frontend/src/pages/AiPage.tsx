import { useState } from "react";
import { api } from "../api/client";
import { AiLoadingIndicator } from "../components/ai/AiLoadingIndicator";
import { AiMessageBubble } from "../components/ai/AiMessageBubble";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { Field, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { useI18n } from "../i18n";
import type { AiLoadingMode, AiMessage } from "../types/ai";
import { createAssistantMessage, createUserMessage } from "../utils/aiMessages";

const aiCapabilityActions = [
  {
    labelKey: "ai.replySuggestion",
    buildMessage: (ticketId: string) => `给 ${ticketId} 号工单生成回复建议`,
  },
  {
    labelKey: "ai.summaryAction",
    buildMessage: (ticketId: string) => `总结 ${ticketId} 号工单`,
  },
  {
    labelKey: "ai.priorityAction",
    buildMessage: (ticketId: string) => `判断 ${ticketId} 号工单的优先级`,
  },
  {
    labelKey: "ai.categoryAction",
    buildMessage: (ticketId: string) => `判断 ${ticketId} 号工单属于什么分类`,
  },
  {
    labelKey: "ai.similarAction",
    buildMessage: (ticketId: string) => `查询 ${ticketId} 号工单的相似工单`,
  },
  {
    labelKey: "ai.slaAction",
    buildMessage: (ticketId: string) => `检查 ${ticketId} 号工单是否有 SLA 风险`,
  },
];

export function AiPage() {
  const [message, setMessage] = useState("List current processing tickets");
  const [ticketId, setTicketId] = useState("1");
  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [conversationId, setConversationId] = useState(() => createConversationId());
  const [loadingMode, setLoadingMode] = useState<AiLoadingMode>(null);
  const [busyPendingMessageId, setBusyPendingMessageId] = useState<string | null>(null);
  const [lastUserMessage, setLastUserMessage] = useState<string | null>(null);
  const { t } = useI18n();

  const sendAiMessage = async (
    nextMessage: string,
    options: { mode?: Exclude<AiLoadingMode, null>; pendingMessageId?: string } = {},
  ) => {
    const trimmed = nextMessage.trim();
    if (!trimmed || loadingMode) return;

    const mode = options.mode ?? "thinking";
    setLastUserMessage(trimmed);
    setMessages((prev) => [...prev, createUserMessage(trimmed)]);
    setLoadingMode(mode);
    setBusyPendingMessageId(options.pendingMessageId ?? null);

    try {
      const data = await api.aiChat(trimmed, conversationId);
      setMessages((prev) => [...prev, createAssistantMessage(data)]);
    } catch (error) {
      setMessages((prev) => [...prev, createAssistantMessage(error, t("ai.requestFailed"))]);
    } finally {
      setLoadingMode(null);
      setBusyPendingMessageId(null);
    }
  };

  const startNewSession = () => {
    if (loadingMode) return;
    setMessages([]);
    setLastUserMessage(null);
    setBusyPendingMessageId(null);
    setConversationId(createConversationId());
  };

  const safeTicketId = ticketId.trim() || "1";
  const canSend = Boolean(message.trim()) && !loadingMode;
  const canRetry = Boolean(lastUserMessage) && !loadingMode;

  return (
    <div>
      <PageHeader
        eyebrow={t("ai.eyebrow")}
        title={t("ai.title")}
        description={t("ai.description")}
        actions={
          <Button variant="secondary" onClick={startNewSession} disabled={Boolean(loadingMode)}>
            {t("ai.newSession")}
          </Button>
        }
      />

      <div className="grid gap-5 px-5 py-5 xl:grid-cols-[1.35fr_0.65fr]">
        <section className="grid min-h-[640px] grid-rows-[auto_1fr_auto] rounded border border-line bg-white">
          <div className="border-b border-line p-5">
            <h2 className="text-base font-semibold">{t("ai.chatHistory")}</h2>
            <p className="mt-1 text-sm text-muted">{t("ai.inputHint")}</p>
          </div>

          <div className="min-h-0 overflow-y-auto p-5">
            {messages.length === 0 ? (
              <EmptyState title={t("ai.noAnswer")} text={t("ai.noAnswerText")} />
            ) : (
              <div className="grid gap-4">
                {messages.map((item) => (
                  <AiMessageBubble
                    key={item.id}
                    message={item}
                    busyMode={busyPendingMessageId === item.id ? loadingMode : null}
                    onConfirm={() => sendAiMessage("确认", { mode: "confirm", pendingMessageId: item.id })}
                    onCancel={() => sendAiMessage("取消", { mode: "cancel", pendingMessageId: item.id })}
                    onRetry={() => {
                      if (lastUserMessage) void sendAiMessage(lastUserMessage);
                    }}
                    canRetry={canRetry}
                  />
                ))}
                <AiLoadingIndicator mode={loadingMode} />
              </div>
            )}
          </div>

          <div className="border-t border-line p-5">
            <div className="grid gap-4">
              <Field label={t("ai.message")}>
                <TextArea
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder={t("ai.placeholder")}
                />
              </Field>
              <div className="flex flex-wrap gap-2">
                <Button variant="primary" onClick={() => void sendAiMessage(message)} disabled={!canSend}>
                  {loadingMode === "thinking" ? t("ai.aiThinking") : t("ai.send")}
                </Button>
              </div>
            </div>
          </div>
        </section>

        <aside className="grid content-start gap-4">
          <section className="rounded border border-line bg-white p-5">
            <h2 className="text-base font-semibold">{t("ai.agentChat")}</h2>
            <div className="mt-4 grid gap-3">
              <Field label={t("ticket.ticketId")}>
                <TextInput value={ticketId} onChange={(event) => setTicketId(event.target.value)} />
              </Field>
              <div className="flex flex-wrap gap-2">
                {aiCapabilityActions.map((action) => (
                  <Button
                    key={action.labelKey}
                    variant="ghost"
                    onClick={() => void sendAiMessage(action.buildMessage(safeTicketId))}
                    disabled={Boolean(loadingMode) || !safeTicketId}
                  >
                    {t(action.labelKey)}
                  </Button>
                ))}
              </div>
            </div>
          </section>

        </aside>
      </div>
    </div>
  );
}

function createConversationId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

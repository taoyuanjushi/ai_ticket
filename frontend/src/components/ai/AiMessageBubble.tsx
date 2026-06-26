import { Bot, UserRound } from "lucide-react";
import { AiErrorCard } from "./AiErrorCard";
import { AiJsonResultCard } from "./AiJsonResultCard";
import { PendingActionCard } from "./PendingActionCard";
import type { AiLoadingMode, AiMessage } from "../../types/ai";
import { useI18n } from "../../i18n";

export function AiMessageBubble({
  message,
  busyMode,
  onConfirm,
  onCancel,
  onRetry,
  canRetry,
}: {
  message: AiMessage;
  busyMode: AiLoadingMode;
  onConfirm: () => void;
  onCancel: () => void;
  onRetry: () => void;
  canRetry: boolean;
}) {
  const { t } = useI18n();
  const isUser = message.role === "user";

  if (isUser) {
    return (
      <article className="flex justify-end">
        <div className="max-w-[82%] rounded bg-brand px-4 py-3 text-sm leading-6 text-white">
          <div className="mb-1 flex items-center justify-end gap-2 text-xs font-semibold text-blue-100">
            {t("ai.userMessage")}
            <UserRound className="h-3.5 w-3.5" aria-hidden="true" />
          </div>
          <p className="whitespace-pre-wrap">{message.content}</p>
        </div>
      </article>
    );
  }

  return (
    <article className="flex justify-start">
      <div className="max-w-[92%] min-w-0">
        <div className="mb-1 flex items-center gap-2 text-xs font-semibold text-muted">
          <Bot className="h-3.5 w-3.5" aria-hidden="true" />
          {t("ai.assistantMessage")}
        </div>
        {message.kind === "pending" ? (
          <PendingActionCard response={message.raw ?? message.content} busyMode={busyMode} onConfirm={onConfirm} onCancel={onCancel} />
        ) : message.kind === "json" ? (
          <AiJsonResultCard response={message.raw ?? message.content} />
        ) : message.kind === "error" || message.kind === "forbidden" ? (
          <AiErrorCard kind={message.kind} message={message.content} canRetry={canRetry} onRetry={onRetry} />
        ) : (
          <div className="rounded border border-blue-100 bg-blue-50 px-4 py-3 text-sm leading-6 text-blue-900">
            <p className="whitespace-pre-wrap">{message.content}</p>
          </div>
        )}
      </div>
    </article>
  );
}

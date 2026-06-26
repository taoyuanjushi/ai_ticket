import { AlertTriangle, CheckCircle2, XCircle } from "lucide-react";
import { Button } from "../Button";
import { useI18n } from "../../i18n";
import { extractPendingAction } from "../../utils/aiMessages";
import type { AiLoadingMode } from "../../types/ai";

interface PendingActionCardProps {
  response: unknown;
  busyMode: AiLoadingMode;
  onConfirm: () => void;
  onCancel: () => void;
}

const actionTypeKeys: Record<string, string> = {
  CREATE_TICKET: "ai.actionCreateTicket",
  UPDATE_TICKET_STATUS: "ai.actionUpdateTicketStatus",
  SAVE_AI_REPLY: "ai.actionSaveAiReply",
  APPLY_AI_CATEGORY: "ai.actionApplyAiCategory",
};

export function PendingActionCard({ response, busyMode, onConfirm, onCancel }: PendingActionCardProps) {
  const { t } = useI18n();
  const action = extractPendingAction(response);
  const actionLabel = action.actionType ? t(actionTypeKeys[action.actionType] ?? "ai.otherAction") : t("ai.otherAction");
  const busy = busyMode === "confirm" || busyMode === "cancel";
  const previewEntries = Object.entries(action.payload ?? {});

  return (
    <div className="rounded border border-amber-200 bg-amber-50 p-4 text-amber-950">
      <div className="flex items-start gap-3">
        <AlertTriangle className="mt-0.5 h-5 w-5 text-amber-600" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">{t("ai.pendingTitle")}</p>
          {action.message ? <p className="mt-1 text-sm leading-6">{action.message}</p> : null}

          <div className="mt-3 grid gap-2 text-sm">
            <InfoRow label={t("ai.operationType")} value={actionLabel} />
            {previewEntries.length > 0 ? (
              <div>
                <p className="font-semibold">{t("ai.operationPreview")}：</p>
                <dl className="mt-1 grid gap-1 rounded border border-amber-200 bg-white/70 p-3">
                  {previewEntries.map(([key, value]) => (
                    <div key={key} className="grid gap-1 sm:grid-cols-[140px_1fr]">
                      <dt className="font-medium text-amber-800">{key}</dt>
                      <dd className="break-words text-amber-950">{stringifyPreview(value)}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            ) : null}
          </div>

          {action.riskFlags.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {action.riskFlags.map((flag) => (
                <span key={flag} className="rounded bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-800 ring-1 ring-amber-200">
                  {flag}
                </span>
              ))}
            </div>
          ) : null}

          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="primary" disabled={busy} onClick={onConfirm}>
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
              {busyMode === "confirm" ? t("ai.confirming") : t("ai.confirmAction")}
            </Button>
            <Button variant="secondary" disabled={busy} onClick={onCancel}>
              <XCircle className="h-4 w-4" aria-hidden="true" />
              {busyMode === "cancel" ? t("ai.canceling") : t("ai.cancelAction")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <p>
      <span className="font-semibold">{label}：</span>
      {value}
    </p>
  );
}

function stringifyPreview(value: unknown) {
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (value === null || value === undefined) return "-";
  return JSON.stringify(value);
}

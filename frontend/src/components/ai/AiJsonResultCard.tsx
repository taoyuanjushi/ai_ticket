import type { ReactNode } from "react";
import { formatPriority, formatTicketStatus, useI18n } from "../../i18n";
import { extractStructuredPayload, isRecord, stringifyScalar, toStringList } from "../../utils/aiMessages";
import { Button } from "../Button";

interface AiJsonResultCardProps {
  response: unknown;
  canSaveAiReply?: boolean;
  canApplyCategory?: boolean;
  onSaveAiReply?: (payload: Record<string, unknown>) => void;
  onApplyCategory?: (payload: Record<string, unknown>) => void;
}

export function AiJsonResultCard({
  response,
  canSaveAiReply = false,
  canApplyCategory = false,
  onSaveAiReply,
  onApplyCategory,
}: AiJsonResultCardProps) {
  const { lang, t } = useI18n();
  const payload = extractStructuredPayload(response);

  if (!payload) {
    return <GenericJsonCard response={response} />;
  }

  if ("similar_tickets" in payload) {
    return <SimilarTicketsCard payload={payload} />;
  }
  if ("sla_risk_level" in payload) {
    return <SlaRiskCard payload={payload} />;
  }
  if ("suggested_priority" in payload) {
    return <AdviceCard title={t("ai.priorityAction")} label={t("ai.suggestedPriority")} value={formatPriority(stringifyScalar(payload.suggested_priority) ?? "", t)} payload={payload} />;
  }
  if ("suggested_category" in payload) {
    return (
      <AdviceCard
        title={t("ai.categoryAction")}
        label={t("ai.suggestedCategory")}
        value={stringifyScalar(payload.suggested_category) ?? "-"}
        payload={payload}
        action={
          canApplyCategory && onApplyCategory ? (
            <Button variant="secondary" onClick={() => onApplyCategory(payload)}>
              {t("actions.applyCategory")}
            </Button>
          ) : null
        }
      />
    );
  }
  if ("summary" in payload) {
    return <SummaryCard payload={payload} />;
  }
  if ("suggestion" in payload) {
    return (
      <AdviceCard
        title={t("ai.replySuggestion")}
        label={t("ai.suggestion")}
        value={stringifyScalar(payload.suggestion) ?? "-"}
        payload={payload}
        action={
          canSaveAiReply && onSaveAiReply ? (
            <Button variant="secondary" onClick={() => onSaveAiReply(payload)}>
              {t("actions.saveAiReply")}
            </Button>
          ) : null
        }
      />
    );
  }

  return <GenericJsonCard response={payload} />;

  function SummaryCard({ payload }: { payload: Record<string, unknown> }) {
    return (
      <ResultShell title={t("ai.summaryAction")} payload={payload}>
        <TextRow label={t("ai.summary")} value={payload.summary} />
        <ListRow label={t("ai.keyPoints")} value={payload.key_points} />
      </ResultShell>
    );
  }

  function SlaRiskCard({ payload }: { payload: Record<string, unknown> }) {
    return (
      <ResultShell title={t("ai.slaAction")} payload={payload}>
        <TextRow label={t("ai.slaRiskLevel")} value={payload.sla_risk_level} strong />
        <TextRow label={t("ai.reason")} value={payload.reason} />
        <ListRow label={t("ai.missingFields")} value={payload.missing_fields} />
      </ResultShell>
    );
  }

  function SimilarTicketsCard({ payload }: { payload: Record<string, unknown> }) {
    const items = Array.isArray(payload.similar_tickets) ? payload.similar_tickets : [];
    return (
      <ResultShell title={t("ai.similarTickets")} payload={payload}>
        {items.length === 0 ? (
          <p className="text-sm text-muted">{t("ai.noSimilarTickets")}</p>
        ) : (
          <div className="grid gap-2">
            {items.map((item, index) => {
              if (!isRecord(item)) return null;
              const id = stringifyScalar(item.id) ?? String(index + 1);
              return (
                <article key={`${id}-${index}`} className="rounded border border-line bg-white p-3">
                  <p className="font-semibold text-ink">
                    #{id} {stringifyScalar(item.title) ?? ""}
                  </p>
                  <p className="mt-1 text-sm text-muted">
                    {formatTicketStatus(stringifyScalar(item.status) ?? "", t)}
                    {item.similarity_reason ? ` · ${stringifyScalar(item.similarity_reason)}` : ""}
                  </p>
                </article>
              );
            })}
          </div>
        )}
      </ResultShell>
    );
  }

  function AdviceCard({
    title,
    label,
    value,
    payload,
    action,
  }: {
    title: string;
    label: string;
    value: string;
    payload: Record<string, unknown>;
    action?: ReactNode;
  }) {
    return (
      <ResultShell title={title} payload={payload}>
        <TextRow label={label} value={value} strong />
        <ConfidenceRow value={payload.confidence} />
        <TextRow label={t("ai.reason")} value={payload.reason} />
        {action ? <div className="flex flex-wrap gap-2 pt-1">{action}</div> : null}
      </ResultShell>
    );
  }

  function ResultShell({
    title,
    payload,
    children,
  }: {
    title: string;
    payload: Record<string, unknown>;
    children: ReactNode;
  }) {
    const riskFlags = toStringList(payload.risk_flags);
    return (
      <div className="rounded border border-slate-200 bg-white p-4 text-sm leading-6">
        <p className="text-sm font-semibold text-ink">{title}</p>
        <div className="mt-3 grid gap-3">{children}</div>
        {riskFlags.length > 0 ? (
          <div className="mt-3">
            <p className="font-semibold text-ink">{t("ai.riskFlags")}{separator(lang)}</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {riskFlags.map((flag) => (
                <span key={flag} className="rounded bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700 ring-1 ring-amber-200">
                  {flag}
                </span>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    );
  }

  function TextRow({ label, value, strong = false }: { label: string; value: unknown; strong?: boolean }) {
    const text = stringifyScalar(value);
    if (!text) return null;
    return (
      <p className={strong ? "font-semibold text-ink" : "text-muted"}>
        <span className="font-semibold text-ink">{label}{separator(lang)}</span>
        {text}
      </p>
    );
  }

  function ConfidenceRow({ value }: { value: unknown }) {
    if (typeof value !== "number" || Number.isNaN(value)) return null;
    const normalized = value <= 1 ? value * 100 : value;
    return <TextRow label={t("ai.confidence")} value={`${Math.round(normalized)}%`} />;
  }

  function ListRow({ label, value }: { label: string; value: unknown }) {
    const items = toStringList(value);
    if (items.length === 0) return null;
    return (
      <div>
        <p className="font-semibold text-ink">{label}{separator(lang)}</p>
        <ul className="mt-1 list-disc space-y-1 pl-5 text-muted">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </div>
    );
  }
}

function GenericJsonCard({ response }: { response: unknown }) {
  const { t } = useI18n();
  return (
    <div className="rounded border border-slate-200 bg-white p-4">
      <p className="text-sm font-semibold text-ink">{t("ai.rawJson")}</p>
      <pre className="mt-3 overflow-auto whitespace-pre-wrap rounded border border-line bg-panel p-3 text-xs text-ink">
        {JSON.stringify(response, null, 2)}
      </pre>
    </div>
  );
}

function separator(lang: string) {
  return lang === "zh" ? "：" : ":";
}

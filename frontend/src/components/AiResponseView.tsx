import type { ReactNode } from "react";

interface AiResponseViewProps {
  response: unknown;
}

export function AiResponseView({ response }: AiResponseViewProps) {
  const payload = parseAiResponse(response);

  if (typeof payload === "string") {
    return <p className="whitespace-pre-wrap text-sm leading-6">{payload}</p>;
  }

  if (!isRecord(payload)) {
    return <p className="whitespace-pre-wrap text-sm leading-6">{String(payload ?? "")}</p>;
  }

  return (
    <div className="grid gap-3 text-sm leading-6">
      {renderMainContent(payload)}
      {renderConfidence(payload.confidence)}
      {renderTextField("依据", payload.reason)}
      {renderListField("关键点", payload.key_points)}
      {renderSimilarTickets(payload.similar_tickets)}
      {renderListField("缺失字段", payload.missing_fields)}
      {renderRiskFlags(payload.risk_flags)}
    </div>
  );
}

export function parseAiResponse(response: unknown): unknown {
  if (typeof response !== "string") {
    return response;
  }

  const text = response.trim();
  if (!text) {
    return "";
  }

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return response;
  }
}

function renderMainContent(payload: Record<string, unknown>) {
  const rows: ReactNode[] = [];
  addTextRow(rows, "建议", payload.suggestion, true);
  addTextRow(rows, "摘要", payload.summary, true);
  addTextRow(rows, "建议优先级", payload.suggested_priority, true);
  addTextRow(rows, "建议分类", payload.suggested_category, true);
  addTextRow(rows, "SLA 风险等级", payload.sla_risk_level, true);

  if (rows.length === 0) {
    rows.push(
      <pre key="json" className="overflow-auto whitespace-pre-wrap rounded border border-line bg-white p-3 text-xs text-ink">
        {JSON.stringify(payload, null, 2)}
      </pre>,
    );
  }

  return rows;
}

function renderConfidence(value: unknown) {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return null;
  }
  const normalized = value <= 1 ? value * 100 : value;
  return (
    <p className="text-muted">
      <span className="font-semibold text-ink">置信度：</span>
      {Math.round(normalized)}%
    </p>
  );
}

function renderTextField(label: string, value: unknown) {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }
  return (
    <p className="text-muted">
      <span className="font-semibold text-ink">{label}：</span>
      {value}
    </p>
  );
}

function renderListField(label: string, value: unknown) {
  const items = toStringList(value);
  if (items.length === 0) {
    return null;
  }
  return (
    <div>
      <p className="font-semibold text-ink">{label}：</p>
      <ul className="mt-1 list-disc space-y-1 pl-5 text-muted">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </div>
  );
}

function renderRiskFlags(value: unknown) {
  const flags = toStringList(value);
  if (flags.length === 0) {
    return null;
  }
  return (
    <div>
      <p className="font-semibold text-ink">风险提示：</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {flags.map((flag) => (
          <span key={flag} className="inline-flex min-h-6 items-center rounded bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700 ring-1 ring-amber-200">
            {flag}
          </span>
        ))}
      </div>
    </div>
  );
}

function renderSimilarTickets(value: unknown) {
  if (!Array.isArray(value) || value.length === 0) {
    return null;
  }
  return (
    <div>
      <p className="font-semibold text-ink">相似工单：</p>
      <div className="mt-2 grid gap-2">
        {value.map((item, index) => {
          if (!isRecord(item)) {
            return null;
          }
          const id = stringifyValue(item.id) || String(index + 1);
          return (
            <article key={`${id}-${index}`} className="rounded border border-line bg-white p-3">
              <p className="font-semibold text-ink">
                #{id} {stringifyValue(item.title)}
              </p>
              <p className="text-muted">
                {stringifyValue(item.status)}
                {item.similarity_reason ? ` · ${stringifyValue(item.similarity_reason)}` : ""}
              </p>
            </article>
          );
        })}
      </div>
    </div>
  );
}

function addTextRow(
  rows: ReactNode[],
  label: string,
  value: unknown,
  strong = false,
) {
  if (typeof value !== "string" || !value.trim()) {
    return;
  }
  rows.push(
    <p key={label} className={strong ? "font-semibold text-ink" : "text-muted"}>
      <span>{label}：</span>
      {value}
    </p>,
  );
}

function toStringList(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .map((item) => stringifyValue(item))
    .filter((item): item is string => Boolean(item));
}

function stringifyValue(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    return value.trim() || null;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}

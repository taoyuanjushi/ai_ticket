import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { useI18n } from "../i18n";
import { operationTypeOptions } from "../types/domain";

export function LogsPage() {
  const { lang, t } = useI18n();
  const [query, setQuery] = useState({
    page: 1,
    size: 10,
    ticketId: "",
    operatorId: "",
    operationSource: "ALL",
    actionType: "ALL",
    resultStatus: "ALL",
    conversationId: "",
  });
  const logsQuery = useQuery({
    queryKey: ["logs", query],
    queryFn: () => api.getOperationLogs(query),
  });

  return (
    <div>
      <PageHeader eyebrow={t("nav.operationLogs")} title={t("operationLog.globalTitle")} description={t("admin.logsDescription")} />
      <div className="px-5 py-5">
        <div className="grid gap-4 rounded border border-line bg-white p-4 xl:grid-cols-[repeat(4,minmax(0,1fr))]">
          <Field label={t("operationLog.ticketId")}>
            <TextInput value={query.ticketId} onChange={(event) => setQuery((prev) => ({ ...prev, ticketId: event.target.value, page: 1 }))} />
          </Field>
          <Field label={t("operationLog.operator")}>
            <TextInput value={query.operatorId} onChange={(event) => setQuery((prev) => ({ ...prev, operatorId: event.target.value, page: 1 }))} />
          </Field>
          <Field label={t("operationLog.operationSource")}>
            <SelectInput value={query.operationSource} onChange={(event) => setQuery((prev) => ({ ...prev, operationSource: event.target.value, page: 1 }))}>
              {["ALL", "MANUAL", "AI"].map((value) => <option key={value} value={value}>{value}</option>)}
            </SelectInput>
          </Field>
          <Field label={t("operationLog.actionType")}>
            <SelectInput value={query.actionType} onChange={(event) => setQuery((prev) => ({ ...prev, actionType: event.target.value, page: 1 }))}>
              {["ALL", ...operationTypeOptions].map((value) => <option key={value} value={value}>{value}</option>)}
            </SelectInput>
          </Field>
          <Field label={t("operationLog.resultStatus")}>
            <SelectInput value={query.resultStatus} onChange={(event) => setQuery((prev) => ({ ...prev, resultStatus: event.target.value, page: 1 }))}>
              {["ALL", "SUCCESS", "FAILED", "CANCELLED", "FORBIDDEN"].map((value) => <option key={value} value={value}>{value}</option>)}
            </SelectInput>
          </Field>
          <Field label={t("operationLog.conversationId")}>
            <TextInput value={query.conversationId} onChange={(event) => setQuery((prev) => ({ ...prev, conversationId: event.target.value, page: 1 }))} />
          </Field>
          <Field label={t("common.pageSize")}>
            <SelectInput value={String(query.size)} onChange={(event) => setQuery((prev) => ({ ...prev, size: Number(event.target.value) }))}>
              <option value="10">10</option>
              <option value="20">20</option>
            </SelectInput>
          </Field>
          <div className="flex items-end">
            <SelectInput value={String(query.page)} onChange={(event) => setQuery((prev) => ({ ...prev, page: Number(event.target.value) }))}>
              <option value="1">{t("admin.page1")}</option>
              <option value="2">{t("admin.page2")}</option>
            </SelectInput>
          </div>
        </div>
      </div>

      <div className="px-5 pb-5">
        <div className="rounded border border-line bg-white">
          {logsQuery.isLoading ? <Loading /> : null}
          {logsQuery.error instanceof Error ? <ErrorNotice message={logsQuery.error.message} /> : null}
          {logsQuery.data ? (
            logsQuery.data.records.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-line">
                  <thead className="bg-panel text-left text-xs font-semibold uppercase tracking-wide text-muted">
                    <tr>
                      <th className="px-4 py-3">{t("operationLog.createdAt")}</th>
                      <th className="px-4 py-3">{t("operationLog.operator")}</th>
                      <th className="px-4 py-3">{t("auth.role")}</th>
                      <th className="px-4 py-3">{t("operationLog.operationSource")}</th>
                      <th className="px-4 py-3">{t("operationLog.actionType")}</th>
                      <th className="px-4 py-3">{t("operationLog.conversationId")}</th>
                      <th className="px-4 py-3">{t("operationLog.target")}</th>
                      <th className="px-4 py-3">{t("operationLog.resultStatus")}</th>
                      <th className="px-4 py-3">{t("operationLog.summary")}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line text-sm">
                    {logsQuery.data.records.map((log) => (
                      <tr key={log.id}>
                        <td className="px-4 py-3">{formatTime(log.createdAt, lang)}</td>
                        <td className="px-4 py-3">{log.operatorName ?? (log.operatorId ? `#${log.operatorId}` : "-")}</td>
                        <td className="px-4 py-3">{log.role ?? "-"}</td>
                        <td className="px-4 py-3">{log.operationSource ?? "MANUAL"}</td>
                        <td className="px-4 py-3">{log.actionType ?? log.action}</td>
                        <td className="px-4 py-3">{log.conversationId ?? "-"}</td>
                        <td className="px-4 py-3">{formatTarget(log.targetType, log.targetId ?? log.ticketId)}</td>
                        <td className="px-4 py-3">{log.resultStatus ?? "-"}</td>
                        <td className="px-4 py-3">{log.requestSummary ?? log.resultSummary ?? log.detail}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title={t("admin.noLogs")} text={t("admin.noLogsText")} />
            )
          ) : null}
        </div>
      </div>
    </div>
  );
}

function formatTime(value: string | undefined, lang: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString(lang === "zh" ? "zh-CN" : "en-US", { hour12: false });
}

function formatTarget(targetType?: string | null, targetId?: number | null) {
  if (!targetType && !targetId) return "-";
  return `${targetType ?? "-"}#${targetId ?? "-"}`;
}

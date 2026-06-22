import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../api/client";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";

export function LogsPage() {
  const [query, setQuery] = useState({
    page: 1,
    size: 10,
    userId: "",
    operationType: "",
    businessType: "",
  });
  const logsQuery = useQuery({
    queryKey: ["logs", query],
    queryFn: () => api.getOperationLogs(query),
  });

  return (
    <div>
      <PageHeader eyebrow="Admin" title="Operation Logs" description="Read-only audit trail for key system actions." />
      <div className="px-5 py-5">
        <div className="grid gap-4 rounded border border-line bg-white p-4 xl:grid-cols-[1fr_1fr_1fr_1fr_auto]">
          <Field label="User ID">
            <TextInput value={query.userId} onChange={(event) => setQuery((prev) => ({ ...prev, userId: event.target.value }))} />
          </Field>
          <Field label="Business Type">
            <SelectInput value={query.businessType} onChange={(event) => setQuery((prev) => ({ ...prev, businessType: event.target.value }))}>
              <option value="">All</option>
              <option value="AUTH">AUTH</option>
              <option value="USER">USER</option>
              <option value="TICKET">TICKET</option>
              <option value="TICKET_REPLY">TICKET_REPLY</option>
            </SelectInput>
          </Field>
          <Field label="Operation Type">
            <TextInput value={query.operationType} onChange={(event) => setQuery((prev) => ({ ...prev, operationType: event.target.value }))} />
          </Field>
          <Field label="Page Size">
            <SelectInput value={String(query.size)} onChange={(event) => setQuery((prev) => ({ ...prev, size: Number(event.target.value) }))}>
              <option value="10">10</option>
              <option value="20">20</option>
            </SelectInput>
          </Field>
          <div className="flex items-end">
            <SelectInput value={String(query.page)} onChange={(event) => setQuery((prev) => ({ ...prev, page: Number(event.target.value) }))}>
              <option value="1">Page 1</option>
              <option value="2">Page 2</option>
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
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">User</th>
                      <th className="px-4 py-3">Business</th>
                      <th className="px-4 py-3">Operation</th>
                      <th className="px-4 py-3">Content</th>
                      <th className="px-4 py-3">Created</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line text-sm">
                    {logsQuery.data.records.map((log) => (
                      <tr key={log.id}>
                        <td className="px-4 py-3">{log.id}</td>
                        <td className="px-4 py-3">{log.userId}</td>
                        <td className="px-4 py-3">{log.businessType}</td>
                        <td className="px-4 py-3">{log.operationType}</td>
                        <td className="px-4 py-3">{log.content}</td>
                        <td className="px-4 py-3">{formatTime(log.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title="No logs" text="No records match the current filters." />
            )
          ) : null}
        </div>
      </div>
    </div>
  );
}

function formatTime(value?: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

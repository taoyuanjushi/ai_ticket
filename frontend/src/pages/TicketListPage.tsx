import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Filter, PlusCircle, RefreshCw, Sparkles, Ticket as TicketIcon } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, priorityOptions, statusOptions } from "../api/client";
import { canCreateTicket, isStaff, normalizeRole } from "../auth/permissions";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { PriorityBadge, StatusBadge } from "../components/Badge";
import { StatCard } from "../components/StatCard";
import { formatPriority, formatTicketStatus, useI18n } from "../i18n";
import { useAuthStore } from "../state/authStore";
import type { TicketPriority, TicketStatus } from "../types/domain";

export function TicketListPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const role = normalizeRole(useAuthStore((state) => state.user?.role));
  const { lang, t } = useI18n();
  const [draft, setDraft] = useState({
    page: Number(params.get("page") ?? 1),
    size: Number(params.get("size") ?? 10),
    status: (params.get("status") ?? "") as TicketStatus | "",
    priority: (params.get("priority") ?? "") as TicketPriority | "",
    category: params.get("category") ?? "",
    keyword: params.get("keyword") ?? "",
  });

  const ticketsQuery = useQuery({
    queryKey: ["tickets", draft],
    queryFn: () => api.getTickets(draft),
  });

  const stats = useMemo(() => {
    const records = ticketsQuery.data?.records ?? [];
    return {
      open: records.filter((ticket) => ticket.status === "OPEN").length,
      processing: records.filter((ticket) => ticket.status === "PROCESSING").length,
      closed: records.filter((ticket) => ticket.status === "CLOSED").length,
      urgent: records.filter((ticket) => ticket.priority === "URGENT").length,
    };
  }, [ticketsQuery.data]);

  return (
    <div className="flex min-h-screen flex-col">
      <PageHeader
        eyebrow={t("ticket.listEyebrow")}
        title={t("ticket.list")}
        description={t("ticket.listDescription")}
        actions={
          <>
            <Button variant="secondary" onClick={() => ticketsQuery.refetch()}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              {t("common.refresh")}
            </Button>
            {canCreateTicket(role) ? (
              <Button variant="primary" onClick={() => navigate("/tickets/new")}>
                <PlusCircle className="h-4 w-4" aria-hidden="true" />
                {t("ticket.newTicket")}
              </Button>
            ) : null}
          </>
        }
      />

      <div className="grid gap-4 px-5 py-4 xl:grid-cols-4">
        <StatCard label={formatTicketStatus("OPEN", t)} value={stats.open} icon={TicketIcon} tone="blue" />
        <StatCard label={formatTicketStatus("PROCESSING", t)} value={stats.processing} icon={Filter} tone="amber" />
        <StatCard label={formatTicketStatus("CLOSED", t)} value={stats.closed} icon={ChevronRight} tone="green" />
        <StatCard label={formatPriority("URGENT", t)} value={stats.urgent} icon={Sparkles} tone="red" />
      </div>

      <div className="px-5 pb-4">
        <div className="grid gap-4 rounded border border-line bg-white p-4 xl:grid-cols-[1.5fr_1fr_1fr_1fr_1fr_auto]">
          <Field label={t("ticket.keyword")}>
            <TextInput
              value={draft.keyword}
              placeholder={t("ticket.keywordPlaceholder")}
              onChange={(event) => setDraft((prev) => ({ ...prev, keyword: event.target.value }))}
            />
          </Field>
          <Field label={t("ticket.status")}>
            <SelectInput
              value={draft.status}
              onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value as TicketStatus | "" }))}
            >
              <option value="">{t("common.all")}</option>
              {statusOptions.map((status) => (
                <option key={status} value={status}>
                  {formatTicketStatus(status, t)}
                </option>
              ))}
            </SelectInput>
          </Field>
          <Field label={t("ticket.priority")}>
            <SelectInput
              value={draft.priority}
              onChange={(event) => setDraft((prev) => ({ ...prev, priority: event.target.value as TicketPriority | "" }))}
            >
              <option value="">{t("common.all")}</option>
              {priorityOptions.map((priority) => (
                <option key={priority} value={priority}>
                  {formatPriority(priority, t)}
                </option>
              ))}
            </SelectInput>
          </Field>
          <Field label={t("ticket.category")}>
            <TextInput
              value={draft.category}
              placeholder="ACCOUNT / SYSTEM"
              onChange={(event) => setDraft((prev) => ({ ...prev, category: event.target.value }))}
            />
          </Field>
          <Field label={t("common.pageSize")}>
            <SelectInput
              value={String(draft.size)}
              onChange={(event) => setDraft((prev) => ({ ...prev, size: Number(event.target.value) }))}
            >
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
            </SelectInput>
          </Field>
          <div className="flex items-end">
            <Button className="w-full" onClick={() => setParams(cleanParams(draft))}>
              {t("common.search")}
            </Button>
          </div>
        </div>
      </div>

      <div className="flex-1 px-5 pb-5">
        <div className="overflow-hidden rounded border border-line bg-white">
          {ticketsQuery.isLoading ? <Loading /> : null}
          {ticketsQuery.error instanceof Error ? <ErrorNotice message={ticketsQuery.error.message} /> : null}

          {!ticketsQuery.isLoading && !ticketsQuery.error ? (
            ticketsQuery.data && ticketsQuery.data.records.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-line">
                  <thead className="bg-panel">
                    <tr className="text-left text-xs font-semibold uppercase tracking-wide text-muted">
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">{t("ticket.titleField")}</th>
                      <th className="px-4 py-3">{t("ticket.status")}</th>
                      <th className="px-4 py-3">{t("ticket.priority")}</th>
                      <th className="px-4 py-3">{t("ticket.category")}</th>
                      <th className="px-4 py-3">{t("common.owner")}</th>
                      <th className="px-4 py-3">{t("common.updated")}</th>
                      <th className="px-4 py-3"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {ticketsQuery.data.records.map((ticket) => (
                      <tr key={ticket.id} className="text-sm hover:bg-slate-50">
                        <td className="px-4 py-3 font-medium text-ink">{ticket.id}</td>
                        <td className="px-4 py-3">
                          <div className="max-w-md">
                            <p className="truncate font-medium text-ink">{ticket.title}</p>
                            <p className="truncate text-xs text-muted">{ticket.content}</p>
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <StatusBadge status={ticket.status} />
                        </td>
                        <td className="px-4 py-3">
                          <PriorityBadge priority={ticket.priority} />
                        </td>
                        <td className="px-4 py-3 text-muted">{ticket.category}</td>
                        <td className="px-4 py-3 text-muted">#{ticket.userId}</td>
                        <td className="px-4 py-3 text-muted">{formatTime(ticket.updatedAt, lang)}</td>
                        <td className="px-4 py-3">
                          <Link className="text-sm font-semibold text-brand" to={`/tickets/${ticket.id}`}>
                            {t("ticket.viewDetail")}
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title={t("ticket.noTickets")} text={t("ticket.noTicketsText")} />
            )
          ) : null}
        </div>
      </div>

      {isStaff(role) ? (
        <div className="sticky bottom-0 border-t border-line bg-white px-5 py-3 text-sm text-muted">
          {t("ticket.staffHint")}
        </div>
      ) : null}
    </div>
  );
}

function cleanParams(draft: {
  page: number;
  size: number;
  status: TicketStatus | "";
  priority: TicketPriority | "";
  category: string;
  keyword: string;
}) {
  const entries = Object.entries(draft).filter(([, value]) => value !== "");
  return Object.fromEntries(entries.map(([key, value]) => [key, String(value)]));
}

function formatTime(value: string | undefined, lang: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString(lang === "zh" ? "zh-CN" : "en-US", { hour12: false });
}

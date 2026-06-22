import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Filter, PlusCircle, RefreshCw, Sparkles, Ticket as TicketIcon } from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, priorityOptions, statusOptions } from "../api/client";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { PriorityBadge, StatusBadge } from "../components/Badge";
import { StatCard } from "../components/StatCard";
import { useAuthStore } from "../state/authStore";
import type { TicketPriority, TicketStatus } from "../types/domain";

export function TicketListPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  const role = useAuthStore((state) => state.user?.role ?? "USER");
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
        eyebrow="Ticket Desk"
        title="Ticket Desk"
        description="Filter, inspect, reply, change status, and use AI assistance from one workspace."
        actions={
          <>
            <Button variant="secondary" onClick={() => ticketsQuery.refetch()}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Refresh
            </Button>
            <Button variant="primary" onClick={() => navigate("/tickets/new")}>
              <PlusCircle className="h-4 w-4" aria-hidden="true" />
              New Ticket
            </Button>
          </>
        }
      />

      <div className="grid gap-4 px-5 py-4 xl:grid-cols-4">
        <StatCard label="Open" value={stats.open} icon={TicketIcon} tone="blue" />
        <StatCard label="Processing" value={stats.processing} icon={Filter} tone="amber" />
        <StatCard label="Closed" value={stats.closed} icon={ChevronRight} tone="green" />
        <StatCard label="Urgent" value={stats.urgent} icon={Sparkles} tone="red" />
      </div>

      <div className="px-5 pb-4">
        <div className="grid gap-4 rounded border border-line bg-white p-4 xl:grid-cols-[1.5fr_1fr_1fr_1fr_1fr_auto]">
          <Field label="Keyword">
            <TextInput
              value={draft.keyword}
              placeholder="Title or content"
              onChange={(event) => setDraft((prev) => ({ ...prev, keyword: event.target.value }))}
            />
          </Field>
          <Field label="Status">
            <SelectInput
              value={draft.status}
              onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value as TicketStatus | "" }))}
            >
              <option value="">All</option>
              {statusOptions.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </SelectInput>
          </Field>
          <Field label="Priority">
            <SelectInput
              value={draft.priority}
              onChange={(event) => setDraft((prev) => ({ ...prev, priority: event.target.value as TicketPriority | "" }))}
            >
              <option value="">All</option>
              {priorityOptions.map((priority) => (
                <option key={priority} value={priority}>
                  {priority}
                </option>
              ))}
            </SelectInput>
          </Field>
          <Field label="Category">
            <TextInput
              value={draft.category}
              placeholder="ACCOUNT / SYSTEM"
              onChange={(event) => setDraft((prev) => ({ ...prev, category: event.target.value }))}
            />
          </Field>
          <Field label="Page Size">
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
              Search
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
                      <th className="px-4 py-3">Title</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Priority</th>
                      <th className="px-4 py-3">Category</th>
                      <th className="px-4 py-3">Owner</th>
                      <th className="px-4 py-3">Updated</th>
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
                        <td className="px-4 py-3 text-muted">{formatTime(ticket.updatedAt)}</td>
                        <td className="px-4 py-3">
                          <Link className="text-sm font-semibold text-brand" to={`/tickets/${ticket.id}`}>
                            Open
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title="No tickets" text="No records match the current filters." />
            )
          ) : null}
        </div>
      </div>

      {role !== "USER" ? (
        <div className="sticky bottom-0 border-t border-line bg-white px-5 py-3 text-sm text-muted">
          Staff and admin users can change status, reply, and delete from the detail page.
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

function formatTime(value?: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

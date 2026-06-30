import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, CornerDownLeft, PencilLine, Trash2, UserCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, priorityOptions, statusOptions } from "../api/client";
import {
  canAssignTicket,
  canDeleteTicket,
  canEditTicket,
  canUpdateTicketStatus,
  canViewTicketLogs,
  isAdmin,
  normalizeRole,
} from "../auth/permissions";
import { TicketAiAssistantPanel } from "../components/ai/TicketAiAssistantPanel";
import { Badge, PriorityBadge, SlaBadge, StatusBadge } from "../components/Badge";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { formatPriority, formatReplyType, formatTicketStatus, useI18n } from "../i18n";
import { useAuthStore } from "../state/authStore";
import type { OperationLog, PageResult, TicketPriority, TicketStatus, User } from "../types/domain";

export function TicketDetailPage() {
  const { id } = useParams();
  const ticketId = Number(id);
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const role = normalizeRole(currentUser?.role);
  const isAdminRole = isAdmin(role);
  const canViewLogs = canViewTicketLogs(role);
  const { lang, t } = useI18n();
  const [reply, setReply] = useState("");
  const [showLogs, setShowLogs] = useState(false);
  const [logPage, setLogPage] = useState(1);
  const [editMode, setEditMode] = useState(false);
  const [editForm, setEditForm] = useState({
    title: "",
    content: "",
    priority: "MEDIUM" as TicketPriority,
    status: "OPEN" as TicketStatus,
    category: "",
  });

  const detailQuery = useQuery({
    queryKey: ["ticket-detail", ticketId],
    queryFn: () => api.getTicketDetail(ticketId),
    enabled: Number.isFinite(ticketId),
  });

  const ticket = detailQuery.data?.ticket;

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: api.getUsers,
    enabled: isAdminRole,
  });

  const assignableUsers = usersQuery.data?.filter((user) => user.role === "STAFF" || user.role === "ADMIN") ?? [];

  const invalidateAssignmentQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["ticket-detail", ticketId] }),
      queryClient.invalidateQueries({ queryKey: ["tickets"] }),
      queryClient.invalidateQueries({ queryKey: ["ticket-logs", ticketId] }),
      queryClient.invalidateQueries({ queryKey: ["logs"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-dashboard-stats"] }),
    ]);
  };

  const logsQuery = useQuery({
    queryKey: ["ticket-logs", ticketId, logPage],
    queryFn: () => api.getTicketLogs(ticketId, { page: logPage, size: 5 }),
    enabled: Number.isFinite(ticketId) && canViewLogs && showLogs,
    retry: false,
  });

  useEffect(() => {
    if (ticket && !editMode) {
      setEditForm({
        title: ticket.title,
        content: ticket.content,
        priority: ticket.priority,
        status: ticket.status,
        category: ticket.category ?? "",
      });
    }
  }, [ticket, editMode]);

  const replyMutation = useMutation({
    mutationFn: () => api.replyTicket(ticketId, reply),
    onSuccess: async () => {
      setReply("");
      await queryClient.invalidateQueries({ queryKey: ["ticket-detail", ticketId] });
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
    },
  });

  const statusMutation = useMutation({
    mutationFn: (status: TicketStatus) => api.updateTicketStatus(ticketId, status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["ticket-detail", ticketId] });
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
    },
  });

  const saveMutation = useMutation({
    mutationFn: () => api.updateTicket(ticketId, editForm),
    onSuccess: async () => {
      setEditMode(false);
      await queryClient.invalidateQueries({ queryKey: ["ticket-detail", ticketId] });
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.deleteTicket(ticketId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
    },
  });

  const assignToMeMutation = useMutation({
    mutationFn: () => api.updateTicketAssignee(ticketId, currentUser?.id ?? null),
    onSuccess: invalidateAssignmentQueries,
  });

  const assigneeMutation = useMutation({
    mutationFn: (assignedTo: number | null) => api.updateTicketAssignee(ticketId, assignedTo),
    onSuccess: invalidateAssignmentQueries,
  });

  if (detailQuery.isLoading) return <Loading label={t("common.loading")} />;
  if (detailQuery.error instanceof Error) return <ErrorNotice message={detailQuery.error.message} />;
  if (!detailQuery.data || !ticket) {
    return <EmptyState title={t("ticket.notFound")} text={t("ticket.notFoundText")} />;
  }

  const canEdit = canEditTicket(role);
  const canUpdateStatus = canUpdateTicketStatus(role);
  const canDelete = canDeleteTicket(role);
  const canAssign = canAssignTicket(role);
  const canReply = ticket.status !== "CLOSED";
  const currentAssignee = ticket.assignedTo
    ? `#${ticket.assignedTo} ${ticket.assignedUserName ?? ""}`.trim()
    : t("ticket.unassigned");
  const selectedAssigneeMissing =
    ticket.assignedTo != null && !assignableUsers.some((user) => user.id === ticket.assignedTo);

  return (
    <div className="flex min-h-screen flex-col">
      <PageHeader
        eyebrow={`#${ticket.id}`}
        title={ticket.title}
        description={t("ticket.detailDescription")}
        actions={
          <>
            <Link to="/" className="text-sm font-semibold text-brand">
              {t("ticket.backToList")}
            </Link>
            {canViewLogs ? (
              <Link to="/logs" className="inline-flex items-center gap-2 text-sm font-semibold text-brand">
                <Activity className="h-4 w-4" aria-hidden="true" />
                {t("actions.viewLogs")}
              </Link>
            ) : null}
            {canEdit ? (
              <Button variant="secondary" onClick={() => setEditMode((value) => !value)}>
                <PencilLine className="h-4 w-4" aria-hidden="true" />
                {editMode ? t("ticket.closeEdit") : t("ticket.editTicket")}
              </Button>
            ) : null}
            {canDelete ? (
              <Button
                variant="danger"
                onClick={() => {
                  if (window.confirm(t("ticket.deleteConfirm"))) {
                    deleteMutation.mutate();
                  }
                }}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
                {t("common.delete")}
              </Button>
            ) : null}
          </>
        }
      />

      <div className="grid gap-5 px-5 py-5 xl:grid-cols-[1.15fr_0.85fr]">
        <section className="grid gap-4">
          <div className="rounded border border-line bg-white p-5">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={ticket.status} />
              <PriorityBadge priority={ticket.priority} />
              <Badge className="bg-slate-50 text-slate-700 ring-slate-200">{ticket.category}</Badge>
            </div>

            {editMode ? (
              <div className="mt-4 grid gap-4">
                <Field label={t("ticket.titleField")}>
                  <TextInput value={editForm.title} onChange={(event) => setEditForm((prev) => ({ ...prev, title: event.target.value }))} />
                </Field>
                <Field label={t("ticket.content")}>
                  <TextArea value={editForm.content} onChange={(event) => setEditForm((prev) => ({ ...prev, content: event.target.value }))} />
                </Field>
                <div className="grid gap-4 md:grid-cols-3">
                  <Field label={t("ticket.priority")}>
                    <SelectInput
                      value={editForm.priority}
                      onChange={(event) => setEditForm((prev) => ({ ...prev, priority: event.target.value as TicketPriority }))}
                    >
                      {priorityOptions.map((item) => (
                        <option key={item} value={item}>
                          {formatPriority(item, t)}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Field label={t("ticket.status")}>
                    <SelectInput
                      value={editForm.status}
                      onChange={(event) => setEditForm((prev) => ({ ...prev, status: event.target.value as TicketStatus }))}
                    >
                      {statusOptions.map((item) => (
                        <option key={item} value={item}>
                          {formatTicketStatus(item, t)}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Field label={t("ticket.category")}>
                    <TextInput value={editForm.category} onChange={(event) => setEditForm((prev) => ({ ...prev, category: event.target.value }))} />
                  </Field>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="secondary" onClick={() => setEditMode(false)}>
                    {t("common.cancel")}
                  </Button>
                  <Button variant="primary" onClick={() => saveMutation.mutate()}>
                    {t("common.save")}
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <p className="mt-4 whitespace-pre-wrap text-sm leading-6 text-ink">{ticket.content}</p>
                <div className="mt-4 grid gap-2 text-sm text-muted sm:grid-cols-3">
                  <InfoLine label={t("common.owner")} value={`#${detailQuery.data.user.id} ${detailQuery.data.user.name}`} />
                  {isAdminRole ? (
                    <div>
                      <Field label={t("ticket.assignee")}>
                        <SelectInput
                          value={ticket.assignedTo ? String(ticket.assignedTo) : ""}
                          disabled={assigneeMutation.isPending || usersQuery.isLoading}
                          onChange={(event) => {
                            const value = event.target.value;
                            assigneeMutation.mutate(value ? Number(value) : null);
                          }}
                        >
                          <option value="">{t("ticket.unassigned")}</option>
                          {selectedAssigneeMissing ? (
                            <option value={ticket.assignedTo ?? ""}>{currentAssignee}</option>
                          ) : null}
                          {assignableUsers.map((user) => (
                            <option key={user.id} value={user.id}>
                              {formatAssigneeOption(user)}
                            </option>
                          ))}
                        </SelectInput>
                      </Field>
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        <span className="text-xs text-muted">{currentAssignee}</span>
                        <Button
                          className="h-8 px-2 text-xs"
                          variant="secondary"
                          disabled={!ticket.assignedTo || assigneeMutation.isPending}
                          onClick={() => assigneeMutation.mutate(null)}
                        >
                          {t("ticket.clearAssignee")}
                        </Button>
                      </div>
                      {usersQuery.error instanceof Error ? (
                        <p className="mt-1 text-xs text-danger">{usersQuery.error.message}</p>
                      ) : null}
                    </div>
                  ) : (
                    <InfoLine label={t("ticket.assignee")} value={currentAssignee} />
                  )}
                  <InfoLine label={t("common.created")} value={formatTime(ticket.createdAt, lang)} />
                  <InfoLine label={t("common.updated")} value={formatTime(ticket.updatedAt, lang)} />
                </div>
                <div className="mt-5 border-t border-line pt-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-ink">{t("ticket.sla")}</span>
                    <SlaBadge status={ticket.slaStatus} />
                  </div>
                  <div className="mt-3 grid gap-2 text-sm text-muted sm:grid-cols-3">
                    <InfoLine label={t("ticket.responseDueAt")} value={formatTime(ticket.responseDueAt ?? undefined, lang)} />
                    <InfoLine label={t("ticket.resolveDueAt")} value={formatTime(ticket.resolveDueAt ?? undefined, lang)} />
                    <InfoLine label={t("ticket.closedAt")} value={formatTime(ticket.closedAt ?? undefined, lang)} />
                    <InfoLine label={t("ticket.slaOverdue")} value={ticket.slaOverdue ? t("ticket.overdue") : t("ticket.notOverdue")} />
                    <InfoLine label={t("ticket.slaRemaining")} value={formatRemainingMinutes(ticket.slaRemainingMinutes, t)} />
                  </div>
                </div>
              </>
            )}
          </div>

          <div className="rounded border border-line bg-white p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-semibold">{t("ticket.replies")}</h2>
              {canReply ? (
                <Badge className="bg-emerald-50 text-emerald-700 ring-emerald-200">{t("ticket.replyEnabled")}</Badge>
              ) : (
                <Badge className="bg-slate-50 text-slate-600 ring-slate-200">{formatTicketStatus("CLOSED", t)}</Badge>
              )}
            </div>
            <div className="mt-4 grid gap-3">
              {detailQuery.data.replies.length > 0 ? (
                detailQuery.data.replies.map((item) => (
                  <article key={item.id} className="rounded border border-line bg-panel p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2 text-sm font-semibold">
                        <ReplyChip type={item.replyType} />
                        <span>{item.authorName?.trim() || `#${item.userId}`}</span>
                        {item.authorRole ? <span className="text-xs font-medium text-muted">{item.authorRole}</span> : null}
                      </div>
                      <span className="text-xs text-muted">{formatTime(item.createdAt, lang)}</span>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-ink">{item.content}</p>
                  </article>
                ))
              ) : (
              <EmptyState title={t("ticket.noReplies")} text={t("ticket.noRepliesText")} />
              )}
            </div>
          </div>

          {canViewLogs ? (
            <TicketLogsPanel
              logs={logsQuery.data}
              isLoading={logsQuery.isLoading}
              error={logsQuery.error}
              showLogs={showLogs}
              page={logPage}
              onToggle={() => setShowLogs((value) => !value)}
              onPageChange={setLogPage}
              lang={lang}
            />
          ) : null}
        </section>

        <aside className="grid gap-4">
          <TicketAiAssistantPanel ticketId={ticket.id} />

          <div className="rounded border border-line bg-white p-5">
            <div className="flex items-center gap-2">
              <CornerDownLeft className="h-4 w-4 text-brand" aria-hidden="true" />
              <h2 className="text-base font-semibold">{t("ticket.quickReply")}</h2>
            </div>
            <Field label={t("ticket.reply")}>
              <TextArea
                value={reply}
                onChange={(event) => setReply(event.target.value)}
                disabled={!canReply}
                placeholder={canReply ? t("ticket.replyPlaceholder") : t("ticket.closedReplyPlaceholder")}
              />
            </Field>
            <div className="mt-3 flex items-center justify-end gap-2">
              {canAssign && !isAdminRole ? (
                <Button
                  variant="secondary"
                  disabled={assignToMeMutation.isPending || ticket.assignedTo === currentUser?.id}
                  onClick={() => assignToMeMutation.mutate()}
                >
                  <UserCheck className="h-4 w-4" aria-hidden="true" />
                  {ticket.assignedTo === currentUser?.id ? t("ticket.assignedToMe") : t("actions.assignTicket")}
                </Button>
              ) : null}
              <Button variant="secondary" disabled={!canReply || !reply.trim()} onClick={() => setReply("")}>
                {t("common.clear")}
              </Button>
              <Button
                variant="primary"
                disabled={!canReply || replyMutation.isPending || !reply.trim()}
                onClick={() => replyMutation.mutate()}
              >
                {t("ticket.sendReply")}
              </Button>
            </div>
            {canUpdateStatus ? (
              <div className="mt-5 grid gap-2">
                <p className="text-sm font-semibold text-ink">{t("actions.updateStatus")}</p>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" disabled={ticket.status !== "OPEN"} onClick={() => statusMutation.mutate("PROCESSING")}>
                    {formatTicketStatus("PROCESSING", t)}
                  </Button>
                  <Button variant="secondary" disabled={ticket.status === "CLOSED"} onClick={() => statusMutation.mutate("CLOSED")}>
                    {t("ticket.closeTicket")}
                  </Button>
                </div>
              </div>
            ) : null}
          </div>
        </aside>
      </div>
    </div>
  );
}

function InfoLine({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-1 text-sm text-ink">{value}</p>
    </div>
  );
}

function formatAssigneeOption(user: User) {
  const displayName = user.name?.trim() || user.username;
  return `#${user.id} ${displayName} (${user.role})`;
}

function ReplyChip({ type }: { type: string }) {
  const { t } = useI18n();
  const tone =
    type === "AI"
      ? "bg-purple-50 text-purple-700 ring-purple-200"
      : type === "STAFF"
        ? "bg-amber-50 text-amber-700 ring-amber-200"
        : "bg-sky-50 text-sky-700 ring-sky-200";
  return <span className={`inline-flex rounded px-2 py-1 text-xs ring-1 ${tone}`}>{formatReplyType(type, t)}</span>;
}

function TicketLogsPanel({
  logs,
  isLoading,
  error,
  showLogs,
  page,
  onToggle,
  onPageChange,
  lang,
}: {
  logs?: PageResult<OperationLog>;
  isLoading: boolean;
  error: unknown;
  showLogs: boolean;
  page: number;
  onToggle: () => void;
  onPageChange: (page: number) => void;
  lang: string;
}) {
  const { t } = useI18n();
  const totalPages = logs ? Math.max(1, Math.ceil(logs.total / logs.size)) : 1;

  return (
    <div className="rounded border border-line bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold">{t("operationLog.title")}</h2>
          <p className="mt-1 text-sm text-muted">{t("operationLog.viewTicketLogs")}</p>
        </div>
        <Button variant="secondary" onClick={onToggle}>
          <Activity className="h-4 w-4" aria-hidden="true" />
          {showLogs ? t("operationLog.hideTicketLogs") : t("operationLog.viewTicketLogs")}
        </Button>
      </div>

      {showLogs ? (
        <div className="mt-4">
          {isLoading ? <Loading label={t("common.loading")} /> : null}
          {error instanceof Error ? <ErrorNotice message={error.message || t("operationLog.loadFailed")} /> : null}
          {logs ? (
            logs.records.length > 0 ? (
              <>
                <div className="overflow-x-auto rounded border border-line">
                  <table className="min-w-full divide-y divide-line text-sm">
                    <thead className="bg-panel text-left text-xs font-semibold uppercase tracking-wide text-muted">
                      <tr>
                        <th className="px-3 py-2">{t("operationLog.createdAt")}</th>
                        <th className="px-3 py-2">{t("operationLog.operator")}</th>
                        <th className="px-3 py-2">{t("operationLog.action")}</th>
                        <th className="px-3 py-2">{t("operationLog.detail")}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-line">
                      {logs.records.map((log) => (
                        <tr key={log.id}>
                          <td className="px-3 py-2 whitespace-nowrap">{formatTime(log.createdAt, lang)}</td>
                          <td className="px-3 py-2">{log.operatorName ?? (log.operatorId ? `#${log.operatorId}` : "-")}</td>
                          <td className="px-3 py-2">{log.action}</td>
                          <td className="px-3 py-2">{log.detail}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="mt-3 flex items-center justify-between gap-2">
                  <span className="text-xs text-muted">
                    {logs.page} / {totalPages}
                  </span>
                  <div className="flex gap-2">
                    <Button variant="secondary" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
                      {t("operationLog.previousPage")}
                    </Button>
                    <Button variant="secondary" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
                      {t("operationLog.nextPage")}
                    </Button>
                  </div>
                </div>
              </>
            ) : (
              <EmptyState title={t("operationLog.noData")} text={t("operationLog.noData")} />
            )
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function formatTime(value: string | undefined, lang: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString(lang === "zh" ? "zh-CN" : "en-US", { hour12: false });
}

function formatRemainingMinutes(value: number | null | undefined, t: (key: string, fallback?: string) => string) {
  if (value == null) return t("ticket.notSet");
  if (value <= 0) return `0m`;
  const hours = Math.floor(value / 60);
  const minutes = value % 60;
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

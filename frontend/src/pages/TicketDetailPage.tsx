import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bot, CornerDownLeft, PencilLine, Sparkles, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, priorityOptions, statusOptions } from "../api/client";
import { AiResponseView } from "../components/AiResponseView";
import { Badge, PriorityBadge, StatusBadge } from "../components/Badge";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { useAuthStore } from "../state/authStore";
import type { ReplySuggestionResponse, TicketPriority, TicketStatus } from "../types/domain";

export function TicketDetailPage() {
  const { id } = useParams();
  const ticketId = Number(id);
  const queryClient = useQueryClient();
  const role = useAuthStore((state) => state.user?.role ?? "USER");
  const [reply, setReply] = useState("");
  const [aiSuggestion, setAiSuggestion] = useState<ReplySuggestionResponse | null>(null);
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

  useEffect(() => {
    if (ticket && !editMode) {
      setEditForm({
        title: ticket.title,
        content: ticket.content,
        priority: ticket.priority,
        status: ticket.status,
        category: ticket.category,
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

  const suggestionMutation = useMutation({
    mutationFn: () => api.replySuggestion(ticketId),
    onSuccess: (data) => {
      const suggestion = data.suggestion ?? "";
      setAiSuggestion(data);
      setReply(suggestion);
    },
  });

  if (detailQuery.isLoading) return <Loading label="Loading ticket detail" />;
  if (detailQuery.error instanceof Error) return <ErrorNotice message={detailQuery.error.message} />;
  if (!detailQuery.data || !ticket) {
    return <EmptyState title="Ticket not found" text="Return to the list and select another ticket." />;
  }

  const canEdit = role === "STAFF" || role === "ADMIN";
  const canDelete = role === "ADMIN";
  const canReply = ticket.status !== "CLOSED";

  return (
    <div className="flex min-h-screen flex-col">
      <PageHeader
        eyebrow={`#${ticket.id}`}
        title={ticket.title}
        description="Ticket detail, replies, status controls, and AI suggestions."
        actions={
          <>
            <Link to="/" className="text-sm font-semibold text-brand">
              Back to list
            </Link>
            {canEdit ? (
              <Button variant="secondary" onClick={() => setEditMode((value) => !value)}>
                <PencilLine className="h-4 w-4" aria-hidden="true" />
                {editMode ? "Close Edit" : "Edit Ticket"}
              </Button>
            ) : null}
            {canDelete ? (
              <Button
                variant="danger"
                onClick={() => {
                  if (window.confirm("Delete this ticket?")) {
                    deleteMutation.mutate();
                  }
                }}
              >
                <Trash2 className="h-4 w-4" aria-hidden="true" />
                Delete
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
                <Field label="Title">
                  <TextInput value={editForm.title} onChange={(event) => setEditForm((prev) => ({ ...prev, title: event.target.value }))} />
                </Field>
                <Field label="Content">
                  <TextArea value={editForm.content} onChange={(event) => setEditForm((prev) => ({ ...prev, content: event.target.value }))} />
                </Field>
                <div className="grid gap-4 md:grid-cols-3">
                  <Field label="Priority">
                    <SelectInput
                      value={editForm.priority}
                      onChange={(event) => setEditForm((prev) => ({ ...prev, priority: event.target.value as TicketPriority }))}
                    >
                      {priorityOptions.map((item) => (
                        <option key={item} value={item}>
                          {item}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Field label="Status">
                    <SelectInput
                      value={editForm.status}
                      onChange={(event) => setEditForm((prev) => ({ ...prev, status: event.target.value as TicketStatus }))}
                    >
                      {statusOptions.map((item) => (
                        <option key={item} value={item}>
                          {item}
                        </option>
                      ))}
                    </SelectInput>
                  </Field>
                  <Field label="Category">
                    <TextInput value={editForm.category} onChange={(event) => setEditForm((prev) => ({ ...prev, category: event.target.value }))} />
                  </Field>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="secondary" onClick={() => setEditMode(false)}>
                    Cancel
                  </Button>
                  <Button variant="primary" onClick={() => saveMutation.mutate()}>
                    Save
                  </Button>
                </div>
              </div>
            ) : (
              <>
                <p className="mt-4 whitespace-pre-wrap text-sm leading-6 text-ink">{ticket.content}</p>
                <div className="mt-4 grid gap-2 text-sm text-muted sm:grid-cols-3">
                  <InfoLine label="Owner" value={`#${detailQuery.data.user.id} ${detailQuery.data.user.name}`} />
                  <InfoLine label="Created" value={formatTime(ticket.createdAt)} />
                  <InfoLine label="Updated" value={formatTime(ticket.updatedAt)} />
                </div>
              </>
            )}
          </div>

          <div className="rounded border border-line bg-white p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-base font-semibold">Reply Timeline</h2>
              {canReply ? (
                <Badge className="bg-emerald-50 text-emerald-700 ring-emerald-200">Reply enabled</Badge>
              ) : (
                <Badge className="bg-slate-50 text-slate-600 ring-slate-200">Closed</Badge>
              )}
            </div>
            <div className="mt-4 grid gap-3">
              {detailQuery.data.replies.length > 0 ? (
                detailQuery.data.replies.map((item) => (
                  <article key={item.id} className="rounded border border-line bg-panel p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2 text-sm font-semibold">
                        <ReplyChip type={item.replyType} />
                        <span>#{item.userId}</span>
                      </div>
                      <span className="text-xs text-muted">{formatTime(item.createdAt)}</span>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-ink">{item.content}</p>
                  </article>
                ))
              ) : (
                <EmptyState title="No replies" text="Start the first reply from the side panel." />
              )}
            </div>
          </div>
        </section>

        <aside className="grid gap-4">
          <div className="rounded border border-line bg-white p-5">
            <div className="flex items-center gap-2">
              <Bot className="h-4 w-4 text-brand" aria-hidden="true" />
              <h2 className="text-base font-semibold">AI Reply Suggestion</h2>
            </div>
            <p className="mt-2 text-sm text-muted">Generate a draft based on the ticket and reply history.</p>
            <Button className="mt-4 w-full" variant="secondary" onClick={() => suggestionMutation.mutate()}>
              <Sparkles className="h-4 w-4" aria-hidden="true" />
              Generate
            </Button>
            {aiSuggestion ? (
              <div className="mt-4 rounded border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-800">
                <AiResponseView response={aiSuggestion} />
              </div>
            ) : null}
          </div>

          <div className="rounded border border-line bg-white p-5">
            <div className="flex items-center gap-2">
              <CornerDownLeft className="h-4 w-4 text-brand" aria-hidden="true" />
              <h2 className="text-base font-semibold">Quick Reply</h2>
            </div>
            <Field label="Reply">
              <TextArea
                value={reply}
                onChange={(event) => setReply(event.target.value)}
                disabled={!canReply}
                placeholder={canReply ? "Type a reply..." : "Closed tickets cannot be replied to"}
              />
            </Field>
            <div className="mt-3 flex items-center justify-end gap-2">
              <Button variant="secondary" disabled={!canReply || !reply.trim()} onClick={() => setReply("")}>
                Clear
              </Button>
              <Button
                variant="primary"
                disabled={!canReply || replyMutation.isPending || !reply.trim()}
                onClick={() => replyMutation.mutate()}
              >
                Send Reply
              </Button>
            </div>
            {canEdit ? (
              <div className="mt-5 grid gap-2">
                <p className="text-sm font-semibold text-ink">Status Transition</p>
                <div className="flex flex-wrap gap-2">
                  <Button variant="secondary" disabled={ticket.status !== "OPEN"} onClick={() => statusMutation.mutate("PROCESSING")}>
                    Processing
                  </Button>
                  <Button variant="secondary" disabled={ticket.status === "CLOSED"} onClick={() => statusMutation.mutate("CLOSED")}>
                    Close
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

function ReplyChip({ type }: { type: string }) {
  const tone =
    type === "AI"
      ? "bg-purple-50 text-purple-700 ring-purple-200"
      : type === "STAFF"
        ? "bg-amber-50 text-amber-700 ring-amber-200"
        : "bg-sky-50 text-sky-700 ring-sky-200";
  return <span className={`inline-flex rounded px-2 py-1 text-xs ring-1 ${tone}`}>{type}</span>;
}

function formatTime(value?: string) {
  if (!value) return "-";
  return new Date(value).toLocaleString("zh-CN", { hour12: false });
}

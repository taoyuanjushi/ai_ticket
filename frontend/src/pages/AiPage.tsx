import { useMutation, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../api/client";
import { AiResponseView } from "../components/AiResponseView";
import { Button } from "../components/Button";
import { EmptyState } from "../components/EmptyState";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, TextArea, TextInput } from "../components/Field";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";

const aiCapabilityActions = [
  {
    label: "总结工单",
    buildMessage: (ticketId: string) => `总结 ${ticketId} 号工单`,
  },
  {
    label: "优先级建议",
    buildMessage: (ticketId: string) => `判断 ${ticketId} 号工单的优先级`,
  },
  {
    label: "分类建议",
    buildMessage: (ticketId: string) => `判断 ${ticketId} 号工单属于什么分类`,
  },
  {
    label: "相似工单",
    buildMessage: (ticketId: string) => `查询 ${ticketId} 号工单的相似工单`,
  },
  {
    label: "SLA 风险",
    buildMessage: (ticketId: string) => `检查 ${ticketId} 号工单是否有 SLA 风险`,
  },
];

export function AiPage() {
  const [message, setMessage] = useState("List current processing tickets");
  const [ticketId, setTicketId] = useState("1");
  const [answer, setAnswer] = useState<unknown>("");
  const [conversationId] = useState(() => createConversationId());

  const chatMutation = useMutation({
    mutationFn: (nextMessage: string) => api.aiChat(nextMessage, conversationId),
    onSuccess: (data) => setAnswer(data.answer ?? data),
  });

  const suggestionQuery = useQuery({
    queryKey: ["ai-suggestion", ticketId],
    queryFn: () => api.replySuggestion(Number(ticketId)),
    enabled: false,
  });

  const sendChatMessage = (nextMessage: string) => {
    setMessage(nextMessage);
    chatMutation.mutate(nextMessage);
  };

  const safeTicketId = ticketId.trim() || "1";

  return (
    <div>
      <PageHeader eyebrow="AI" title="AI Assistant" description="Chat with the agent and generate ticket reply suggestions." />
      <div className="grid gap-5 px-5 py-5 xl:grid-cols-2">
        <section className="rounded border border-line bg-white p-5">
          <h2 className="text-base font-semibold">Agent Chat</h2>
          <div className="mt-4 grid gap-4">
            <Field label="Message">
              <TextArea value={message} onChange={(event) => setMessage(event.target.value)} />
            </Field>
            <div className="flex flex-wrap gap-2">
              <Button variant="primary" onClick={() => sendChatMessage(message)} disabled={!message.trim() || chatMutation.isPending}>
                Send
              </Button>
              <Button variant="secondary" onClick={() => sendChatMessage("确认")} disabled={chatMutation.isPending}>
                Confirm
              </Button>
              <Button variant="secondary" onClick={() => sendChatMessage("取消")} disabled={chatMutation.isPending}>
                Cancel
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              {aiCapabilityActions.map((action) => (
                <Button
                  key={action.label}
                  variant="ghost"
                  onClick={() => sendChatMessage(action.buildMessage(safeTicketId))}
                  disabled={chatMutation.isPending || !safeTicketId}
                >
                  {action.label}
                </Button>
              ))}
            </div>
            {chatMutation.isPending ? <Loading /> : null}
            {chatMutation.error instanceof Error ? <ErrorNotice message={chatMutation.error.message} /> : null}
            {answer ? (
              <div className="rounded border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-800">
                <AiResponseView response={answer} />
              </div>
            ) : (
              <EmptyState title="No answer yet" text="Send a ticket-related prompt to begin." />
            )}
          </div>
        </section>

        <section className="rounded border border-line bg-white p-5">
          <h2 className="text-base font-semibold">Reply Suggestion</h2>
          <div className="mt-4 grid gap-4">
            <Field label="Ticket ID">
              <TextInput value={ticketId} onChange={(event) => setTicketId(event.target.value)} />
            </Field>
            <Button variant="secondary" onClick={() => suggestionQuery.refetch()}>
              Generate
            </Button>
            {suggestionQuery.isFetching ? <Loading /> : null}
            {suggestionQuery.error instanceof Error ? <ErrorNotice message={suggestionQuery.error.message} /> : null}
            {suggestionQuery.data ? (
              <div className="grid gap-3 rounded border border-slate-200 bg-panel p-4 text-sm">
                <AiResponseView response={suggestionQuery.data} />
              </div>
            ) : (
              <EmptyState title="No suggestion" text="Enter a ticket ID and generate a draft." />
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function createConversationId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

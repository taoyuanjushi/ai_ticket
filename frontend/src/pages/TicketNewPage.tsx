import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, priorityOptions } from "../api/client";
import { Button } from "../components/Button";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import type { TicketPriority } from "../types/domain";

export function TicketNewPage() {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [category, setCategory] = useState("OTHER");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => api.createTicket({ title, content, priority, category }),
    onSuccess: async (ticket) => {
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
      navigate(`/tickets/${ticket.id}`);
    },
    onError: (err: unknown) => {
      setError(err instanceof Error ? err.message : "Create failed");
    },
  });

  return (
    <div>
      <PageHeader
        eyebrow="Create"
        title="Create Ticket"
        description="Title and content are required. Priority and category are optional."
      />
      <div className="max-w-3xl px-5 py-5">
        <div className="grid gap-4 rounded border border-line bg-white p-5">
          {error ? <ErrorNotice message={error} /> : null}
          <Field label="Title">
            <TextInput value={title} onChange={(event) => setTitle(event.target.value)} />
          </Field>
          <Field label="Content">
            <TextArea value={content} onChange={(event) => setContent(event.target.value)} />
          </Field>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="Priority">
              <SelectInput value={priority} onChange={(event) => setPriority(event.target.value as TicketPriority)}>
                {priorityOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </SelectInput>
            </Field>
            <Field label="Category">
              <TextInput value={category} onChange={(event) => setCategory(event.target.value)} />
            </Field>
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => navigate(-1)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                setError("");
                mutation.mutate();
              }}
              disabled={mutation.isPending || !title.trim() || !content.trim()}
            >
              {mutation.isPending ? "Creating..." : "Create Ticket"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

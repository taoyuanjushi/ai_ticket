import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, priorityOptions } from "../api/client";
import { Button } from "../components/Button";
import { ErrorNotice } from "../components/ErrorNotice";
import { Field, SelectInput, TextArea, TextInput } from "../components/Field";
import { PageHeader } from "../components/PageHeader";
import { formatPriority, useI18n } from "../i18n";
import type { TicketPriority } from "../types/domain";

export function TicketNewPage() {
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [priority, setPriority] = useState<TicketPriority>("MEDIUM");
  const [category, setCategory] = useState("OTHER");
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { t } = useI18n();

  const mutation = useMutation({
    mutationFn: () => api.createTicket({ title, content, priority, category }),
    onSuccess: async (ticket) => {
      await queryClient.invalidateQueries({ queryKey: ["tickets"] });
      navigate(`/tickets/${ticket.id}`);
    },
    onError: (err: unknown) => {
      setError(err instanceof Error ? err.message : t("ticket.createFailed"));
    },
  });

  return (
    <div>
      <PageHeader
        eyebrow={t("ticket.createEyebrow")}
        title={t("ticket.create")}
        description={t("ticket.createDescription")}
      />
      <div className="max-w-3xl px-5 py-5">
        <div className="grid gap-4 rounded border border-line bg-white p-5">
          {error ? <ErrorNotice message={error} /> : null}
          <Field label={t("ticket.titleField")}>
            <TextInput value={title} onChange={(event) => setTitle(event.target.value)} />
          </Field>
          <Field label={t("ticket.content")}>
            <TextArea value={content} onChange={(event) => setContent(event.target.value)} />
          </Field>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label={t("ticket.priority")}>
              <SelectInput value={priority} onChange={(event) => setPriority(event.target.value as TicketPriority)}>
                {priorityOptions.map((item) => (
                  <option key={item} value={item}>
                    {formatPriority(item, t)}
                  </option>
                ))}
              </SelectInput>
            </Field>
            <Field label={t("ticket.category")}>
              <TextInput value={category} onChange={(event) => setCategory(event.target.value)} />
            </Field>
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => navigate(-1)}>
              {t("common.cancel")}
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                setError("");
                mutation.mutate();
              }}
              disabled={mutation.isPending || !title.trim() || !content.trim()}
            >
              {mutation.isPending ? t("ticket.creating") : t("ticket.create")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

import { Inbox } from "lucide-react";

export function EmptyState({ title, text }: { title: string; text?: string }) {
  return (
    <div className="flex min-h-48 flex-col items-center justify-center gap-3 rounded border border-dashed border-line bg-white p-8 text-center">
      <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
      <div>
        <p className="text-sm font-semibold text-ink">{title}</p>
        {text ? <p className="mt-1 text-sm text-muted">{text}</p> : null}
      </div>
    </div>
  );
}

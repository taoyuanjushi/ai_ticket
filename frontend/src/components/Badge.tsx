import { clsx } from "clsx";
import type { ReactNode } from "react";
import type { TicketPriority, TicketStatus } from "../types/domain";

const statusClasses: Record<TicketStatus, string> = {
  OPEN: "bg-sky-50 text-sky-700 ring-sky-200",
  PROCESSING: "bg-amber-50 text-amber-700 ring-amber-200",
  CLOSED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
};

const priorityClasses: Record<TicketPriority, string> = {
  LOW: "bg-slate-50 text-slate-600 ring-slate-200",
  MEDIUM: "bg-blue-50 text-blue-700 ring-blue-200",
  HIGH: "bg-orange-50 text-orange-700 ring-orange-200",
  URGENT: "bg-red-50 text-red-700 ring-red-200",
};

export function StatusBadge({ status }: { status: TicketStatus }) {
  return <Badge className={statusClasses[status]}>{status}</Badge>;
}

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  return <Badge className={priorityClasses[priority]}>{priority}</Badge>;
}

export function Badge({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={clsx(
        "inline-flex h-6 items-center rounded px-2 text-xs font-semibold ring-1",
        className,
      )}
    >
      {children}
    </span>
  );
}

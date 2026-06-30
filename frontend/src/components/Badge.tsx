import { clsx } from "clsx";
import type { ReactNode } from "react";
import { formatPriority, formatSlaStatus, formatTicketStatus, useI18n } from "../i18n";
import type { TicketPriority, TicketSlaStatus, TicketStatus } from "../types/domain";

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

const slaClasses: Record<TicketSlaStatus, string> = {
  NO_SLA: "bg-slate-50 text-slate-600 ring-slate-200",
  ON_TRACK: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  AT_RISK: "bg-amber-50 text-amber-700 ring-amber-200",
  OVERDUE: "bg-red-50 text-red-700 ring-red-200",
  COMPLETED: "bg-slate-50 text-slate-600 ring-slate-200",
};

export function StatusBadge({ status }: { status: TicketStatus }) {
  const { t } = useI18n();
  return <Badge className={statusClasses[status]}>{formatTicketStatus(status, t)}</Badge>;
}

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  const { t } = useI18n();
  return <Badge className={priorityClasses[priority]}>{formatPriority(priority, t)}</Badge>;
}

export function SlaBadge({ status }: { status?: TicketSlaStatus | null }) {
  const { t } = useI18n();
  const safeStatus = status ?? "NO_SLA";
  return <Badge className={slaClasses[safeStatus]}>{formatSlaStatus(safeStatus, t)}</Badge>;
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

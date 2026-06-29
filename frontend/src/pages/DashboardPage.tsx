import { useQuery } from "@tanstack/react-query";
import {
  CheckCircle2,
  Clock3,
  Flame,
  ListTodo,
  Sparkles,
  Ticket,
  Zap,
} from "lucide-react";
import { api } from "../api/client";
import { ApiError } from "../api/http";
import { ErrorNotice } from "../components/ErrorNotice";
import { Loading } from "../components/Loading";
import { PageHeader } from "../components/PageHeader";
import { StatCard } from "../components/StatCard";
import { useI18n } from "../i18n";
import type { DashboardStats } from "../types/domain";

const emptyStats: Required<DashboardStats> = {
  ticketTotal: 0,
  pendingCount: 0,
  processingCount: 0,
  doneCount: 0,
  closedCount: 0,
  highPriorityCount: 0,
  urgentPriorityCount: 0,
  aiSuggestionCount: 0,
  aiAcceptedCount: 0,
  aiAcceptanceRate: 0,
};

export function DashboardPage() {
  const { t } = useI18n();
  const statsQuery = useQuery({
    queryKey: ["admin-dashboard-stats"],
    queryFn: api.getDashboardStats,
  });
  const stats = { ...emptyStats, ...(statsQuery.data ?? {}) };
  const errorMessage =
    statsQuery.error instanceof ApiError && statsQuery.error.status === 403
      ? t("dashboard.forbidden")
      : statsQuery.error instanceof Error
        ? statsQuery.error.message
        : t("dashboard.loadFailed");

  return (
    <div>
      <PageHeader eyebrow={t("dashboard.eyebrow")} title={t("dashboard.title")} description={t("dashboard.description")} />
      <div className="grid gap-4 px-5 py-5 sm:grid-cols-2 xl:grid-cols-5">
        <StatCard label={t("dashboard.ticketTotal")} value={numberValue(stats.ticketTotal)} icon={Ticket} tone="blue" />
        <StatCard label={t("dashboard.pendingCount")} value={numberValue(stats.pendingCount)} icon={ListTodo} tone="amber" />
        <StatCard label={t("dashboard.processingCount")} value={numberValue(stats.processingCount)} icon={Clock3} tone="blue" />
        <StatCard label={t("dashboard.doneCount")} value={numberValue(stats.doneCount)} icon={CheckCircle2} tone="green" />
        <StatCard label={t("dashboard.closedCount")} value={numberValue(stats.closedCount)} icon={CheckCircle2} tone="green" />
        <StatCard label={t("dashboard.highPriorityCount")} value={numberValue(stats.highPriorityCount)} icon={Flame} tone="red" />
        <StatCard label={t("dashboard.urgentPriorityCount")} value={numberValue(stats.urgentPriorityCount)} icon={Zap} tone="red" />
        <StatCard label={t("dashboard.aiSuggestionCount")} value={numberValue(stats.aiSuggestionCount)} icon={Sparkles} tone="blue" />
        <StatCard label={t("dashboard.aiAcceptedCount")} value={numberValue(stats.aiAcceptedCount)} icon={Sparkles} tone="green" />
        <StatCard label={t("dashboard.aiAcceptanceRate")} value={percentValue(stats.aiAcceptanceRate)} icon={Sparkles} tone="amber" />
      </div>
      <div className="px-5">
        {statsQuery.isLoading ? <Loading /> : null}
        {statsQuery.error ? <ErrorNotice message={errorMessage} /> : null}
      </div>
    </div>
  );
}

function numberValue(value: number | null | undefined) {
  return value ?? 0;
}

function percentValue(value: number | null | undefined) {
  return `${((value ?? 0) * 100).toFixed(1)}%`;
}


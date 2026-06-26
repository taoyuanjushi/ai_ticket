import { Database, Server, ShieldCheck } from "lucide-react";
import { PageHeader } from "../components/PageHeader";
import { StatCard } from "../components/StatCard";
import { useI18n } from "../i18n";

export function SettingsPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader eyebrow={t("settings.eyebrow")} title={t("settings.title")} description={t("settings.description")} />
      <div className="grid gap-4 px-5 py-5 md:grid-cols-3">
        <StatCard label={t("settings.javaApi")} value="8080" icon={Server} tone="blue" />
        <StatCard label={t("settings.pythonAi")} value={t("settings.viaJava")} icon={Database} tone="amber" />
        <StatCard label={t("settings.auth")} value="JWT" icon={ShieldCheck} tone="green" />
      </div>
      <div className="mx-5 rounded border border-line bg-white p-5 text-sm leading-6 text-muted">
        <p>{t("settings.text")}</p>
      </div>
    </div>
  );
}

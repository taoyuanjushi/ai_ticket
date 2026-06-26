import { useI18n } from "../i18n";

export function Loading({ label = "Loading" }: { label?: string }) {
  const { t } = useI18n();

  return (
    <div className="flex min-h-32 items-center justify-center text-sm text-muted">
      <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-brand" />
      {label === "Loading" ? t("common.loading") : label}
    </div>
  );
}

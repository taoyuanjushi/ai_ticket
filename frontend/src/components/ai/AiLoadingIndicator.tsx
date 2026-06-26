import type { AiLoadingMode } from "../../types/ai";
import { useI18n } from "../../i18n";

export function AiLoadingIndicator({ mode }: { mode: AiLoadingMode }) {
  const { t } = useI18n();
  if (!mode) return null;

  const label =
    mode === "confirm" ? t("ai.confirming") : mode === "cancel" ? t("ai.canceling") : t("ai.aiThinking");

  return (
    <div className="flex items-center gap-2 rounded border border-blue-100 bg-blue-50 px-3 py-2 text-sm font-medium text-blue-800">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-blue-200 border-t-blue-700" />
      {label}
    </div>
  );
}

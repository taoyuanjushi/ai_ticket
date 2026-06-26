import { AlertCircle, ShieldAlert } from "lucide-react";
import { Button } from "../Button";
import { useI18n } from "../../i18n";

export function AiErrorCard({
  kind,
  message,
  canRetry,
  onRetry,
}: {
  kind: "error" | "forbidden";
  message: string;
  canRetry: boolean;
  onRetry: () => void;
}) {
  const { t } = useI18n();
  const forbidden = kind === "forbidden";
  const Icon = forbidden ? ShieldAlert : AlertCircle;

  return (
    <div className={`rounded border p-4 ${forbidden ? "border-red-200 bg-red-50 text-red-800" : "border-rose-200 bg-rose-50 text-rose-800"}`}>
      <div className="flex items-start gap-3">
        <Icon className="mt-0.5 h-5 w-5" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold">{forbidden ? t("ai.forbiddenTitle") : t("ai.errorTitle")}</p>
          <p className="mt-1 whitespace-pre-wrap text-sm leading-6">{message}</p>
          {!forbidden && canRetry ? (
            <Button className="mt-3" variant="secondary" onClick={onRetry}>
              {t("ai.retry")}
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

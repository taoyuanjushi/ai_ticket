import { Languages } from "lucide-react";
import { useI18n } from "../i18n";

export function LanguageSwitcher() {
  const { lang, setLang, t } = useI18n();
  const nextLang = lang === "zh" ? "en" : "zh";

  return (
    <button
      type="button"
      className="inline-flex h-9 items-center justify-center gap-2 rounded border border-line bg-white px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
      onClick={() => setLang(nextLang)}
      aria-label={t("common.language")}
      title={t("common.language")}
    >
      <Languages className="h-4 w-4" aria-hidden="true" />
      {lang === "zh" ? "English" : "中文"}
    </button>
  );
}

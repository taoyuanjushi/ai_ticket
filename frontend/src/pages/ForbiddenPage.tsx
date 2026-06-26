import { Link } from "react-router-dom";
import { Button } from "../components/Button";
import { PageHeader } from "../components/PageHeader";
import { useI18n } from "../i18n";

export function ForbiddenPage() {
  const { t } = useI18n();

  return (
    <div>
      <PageHeader eyebrow="403" title={t("forbidden.title")} description={t("forbidden.description")} />
      <div className="px-5 py-5">
        <Link to="/">
          <Button variant="primary">{t("forbidden.back")}</Button>
        </Link>
      </div>
    </div>
  );
}

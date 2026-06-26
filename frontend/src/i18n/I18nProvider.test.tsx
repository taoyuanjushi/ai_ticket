import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";
import { LanguageSwitcher } from "../components/LanguageSwitcher";
import { StatusBadge } from "../components/Badge";
import { useAuthStore } from "../state/authStore";
import { I18nProvider, languageStorageKey, useI18n } from ".";

function renderProbe() {
  return render(
    <I18nProvider>
      <LanguageSwitcher />
      <ProbeText />
      <StatusBadge status="OPEN" />
    </I18nProvider>,
  );
}

function ProbeText() {
  const { t } = useI18n();
  return <p>{t("nav.ticketDesk")}</p>;
}

describe("I18nProvider", () => {
  afterEach(() => {
    localStorage.removeItem(languageStorageKey);
    useAuthStore.getState().clearSession();
  });

  it("uses Chinese by default", () => {
    renderProbe();

    expect(screen.getByText("工单台")).toBeInTheDocument();
    expect(screen.getByText("待处理")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "语言" })).toHaveTextContent("English");
  });

  it("switches to English and saves language in localStorage", async () => {
    renderProbe();

    await userEvent.click(screen.getByRole("button", { name: "语言" }));

    expect(screen.getByText("Ticket Desk")).toBeInTheDocument();
    expect(screen.getByText("Open")).toBeInTheDocument();
    expect(localStorage.getItem(languageStorageKey)).toBe("en");
  });

  it("loads the saved language when mounted again", () => {
    localStorage.setItem(languageStorageKey, "en");

    renderProbe();

    expect(screen.getByText("Ticket Desk")).toBeInTheDocument();
    expect(screen.getByText("Open")).toBeInTheDocument();
  });

  it("does not clear token when language changes", async () => {
    useAuthStore.getState().setSession("token-kept", {
      id: 1,
      username: "staff01",
      name: "Staff One",
      email: "staff01@example.com",
      role: "STAFF",
    });

    renderProbe();
    await userEvent.click(screen.getByRole("button", { name: "语言" }));

    expect(useAuthStore.getState().token).toBe("token-kept");
    expect(localStorage.getItem("ticketdesk.token")).toBe("token-kept");
  });
});

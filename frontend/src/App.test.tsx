import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowserRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { App } from "./App";
import { I18nProvider } from "./i18n";
import { useAuthStore } from "./state/authStore";

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

describe("App", () => {
  it("shows login when no token is stored", () => {
    useAuthStore.getState().clearSession();
    renderApp();
    expect(screen.getByText("进入工单台")).toBeInTheDocument();
  });

  it("logs in with mock credentials and shows the ticket desk", async () => {
    useAuthStore.getState().clearSession();
    renderApp();
    await userEvent.click(screen.getByText("进入工单台"));
    expect(await screen.findByRole("heading", { name: "工单列表" })).toBeInTheDocument();
    expect(await screen.findByText("Login failure")).toBeInTheDocument();
  });
});

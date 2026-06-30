import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { I18nProvider, languageStorageKey } from "../i18n";
import { useAuthStore } from "../state/authStore";
import type { PageResult, Ticket } from "../types/domain";
import { TicketListPage } from "./TicketListPage";

const emptyTickets: PageResult<Ticket> = {
  records: [],
  total: 0,
  page: 1,
  size: 10,
};

function renderTicketList(initialPath = "/?page=2") {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter initialEntries={[initialPath]}>
          <TicketListPage />
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

function setCurrentUser(role: "USER" | "STAFF" | "ADMIN") {
  useAuthStore.getState().setSession(`${role.toLowerCase()}-token`, {
    id: role === "USER" ? 1 : role === "STAFF" ? 2 : 3,
    username: role === "USER" ? "tom" : role === "STAFF" ? "staff01" : "admin01",
    name: role === "USER" ? "Tom" : role === "STAFF" ? "Staff One" : "Admin One",
    email: role === "USER" ? "tom@example.com" : role === "STAFF" ? "staff01@example.com" : "admin01@example.com",
    role,
  });
}

describe("TicketListPage assignee filters", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.removeItem(languageStorageKey);
    useAuthStore.getState().clearSession();
  });

  it("sends assignedTo=unassigned and resets page when staff filters by assignee", async () => {
    localStorage.setItem(languageStorageKey, "en");
    setCurrentUser("STAFF");
    const ticketsMock = vi.spyOn(api, "getTickets").mockResolvedValue(emptyTickets);

    renderTicketList("/?page=2");

    await screen.findByRole("heading", { name: "Ticket List" });
    await userEvent.selectOptions(screen.getByRole("combobox", { name: "Assignee" }), "unassigned");

    await waitFor(() =>
      expect(ticketsMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 1,
          assignedTo: "unassigned",
        }),
      ),
    );
  });

  it("shows assignable staff and admin users to admin only", async () => {
    localStorage.setItem(languageStorageKey, "en");
    setCurrentUser("ADMIN");
    vi.spyOn(api, "getTickets").mockResolvedValue(emptyTickets);
    vi.spyOn(api, "getUsers").mockResolvedValue([
      { id: 1, username: "tom", name: "Tom", email: "tom@example.com", role: "USER" },
      { id: 2, username: "staff01", name: "Staff One", email: "staff01@example.com", role: "STAFF" },
      { id: 3, username: "admin01", name: "Admin One", email: "admin01@example.com", role: "ADMIN" },
    ]);

    renderTicketList();

    const assigneeSelect = await screen.findByRole("combobox", { name: "Assignee" });
    expect(await screen.findByRole("option", { name: "#2 Staff One (STAFF)" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "#3 Admin One (ADMIN)" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /Tom/ })).not.toBeInTheDocument();

    await userEvent.selectOptions(assigneeSelect, "2");
    await waitFor(() =>
      expect(api.getTickets).toHaveBeenLastCalledWith(
        expect.objectContaining({
          assignedTo: "2",
        }),
      ),
    );
  });

  it("does not show the assignee filter to normal users", async () => {
    localStorage.setItem(languageStorageKey, "en");
    setCurrentUser("USER");
    vi.spyOn(api, "getTickets").mockResolvedValue(emptyTickets);
    const usersMock = vi.spyOn(api, "getUsers");

    renderTicketList();

    expect(await screen.findByRole("heading", { name: "Ticket List" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Assignee" })).not.toBeInTheDocument();
    expect(usersMock).not.toHaveBeenCalled();
  });
});

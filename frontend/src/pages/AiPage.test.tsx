import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { AiPage } from "./AiPage";

function renderAiPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AiPage />
    </QueryClientProvider>,
  );
}

describe("AiPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders reply suggestion risk flags without legacy fields", async () => {
    vi.spyOn(api, "replySuggestion").mockResolvedValue({
      suggestion: "建议先确认问题是否仍然存在。",
      confidence: 0.82,
      reason: "基于工单详情和历史回复生成。",
      risk_flags: ["信息不足", "需要人工确认"],
    });

    renderAiPage();

    await userEvent.click(screen.getByRole("button", { name: "Generate" }));

    expect(await screen.findByText("建议先确认问题是否仍然存在。")).toBeInTheDocument();
    expect(screen.getByText("82%")).toBeInTheDocument();
    expect(screen.getByText("基于工单详情和历史回复生成。")).toBeInTheDocument();
    expect(screen.getByText("信息不足")).toBeInTheDocument();
    expect(screen.getByText("需要人工确认")).toBeInTheDocument();
  });

  it("uses the same conversationId for confirm and cancel", async () => {
    const originalCrypto = globalThis.crypto;
    vi.stubGlobal("crypto", {
      ...originalCrypto,
      randomUUID: () => "chat-test-id",
    });
    const aiChatMock = vi.spyOn(api, "aiChat").mockResolvedValue({ answer: "ok" });

    renderAiPage();

    await userEvent.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(2));

    expect(aiChatMock.mock.calls[0]).toEqual(["确认", "chat-test-id"]);
    expect(aiChatMock.mock.calls[1]).toEqual(["取消", "chat-test-id"]);
  });
});

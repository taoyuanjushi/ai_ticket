import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import { ApiError } from "../../api/http";
import { LanguageSwitcher } from "../LanguageSwitcher";
import { I18nProvider, languageStorageKey } from "../../i18n";
import { useAuthStore } from "../../state/authStore";
import type { TicketDetail } from "../../types/domain";
import { TicketDetailPage } from "../../pages/TicketDetailPage";
import { TicketAiAssistantPanel } from "./TicketAiAssistantPanel";

const originalSuggestion = "请用户补充发票编号和截图。";
const editedSuggestion = "这是 STAFF 编辑后的测试回复。";
const suggestionReason = "当前描述缺少复现细节。";
const riskFlags = ["需要人工确认"];

function renderPanel({ withLanguageSwitcher = false } = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        {withLanguageSwitcher ? <LanguageSwitcher /> : null}
        <TicketAiAssistantPanel ticketId={3} />
      </I18nProvider>
    </QueryClientProvider>,
  );
}

function renderTicketDetailPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <MemoryRouter initialEntries={["/tickets/3"]}>
          <Routes>
            <Route path="/tickets/:id" element={<TicketDetailPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    </QueryClientProvider>,
  );
}

function stubStableCrypto() {
  let count = 0;
  vi.stubGlobal("crypto", {
    randomUUID: () => (count++ === 0 ? "detail-conversation" : `id-${count}`),
  });
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

function expectTextContent(text: string) {
  expect(
    screen.getByText((_, node) => Boolean(node && node.textContent === text)),
  ).toBeInTheDocument();
}

function replySuggestionResponse() {
  return {
    type: "JSON_RESULT",
    message: "回复建议生成完成",
    data: {
      suggestion: originalSuggestion,
      confidence: 0.82,
      reason: suggestionReason,
      risk_flags: riskFlags,
    },
    risk_flags: riskFlags,
  };
}

const ticketDetail: TicketDetail = {
  ticket: {
    id: 3,
    title: "Invoice data not saved",
    content: "Invoice fields disappear after refresh.",
    status: "OPEN",
    priority: "URGENT",
    category: "BILLING",
    userId: 1,
    createdAt: "2026-06-23T10:00:00",
    updatedAt: "2026-06-23T10:00:00",
  },
  user: {
    id: 1,
    username: "tom",
    name: "Tom",
    email: "tom@example.com",
    role: "USER",
  },
  replies: [],
};

describe("TicketAiAssistantPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.removeItem(languageStorageKey);
    useAuthStore.getState().clearSession();
  });

  it("renders inside the ticket detail page", async () => {
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);

    renderTicketDetailPage();

    expect(await screen.findByRole("heading", { name: "AI 辅助" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "生成回复建议" })).toBeInTheDocument();
  });

  it("hides internal ticket actions from USER on the detail page", async () => {
    setCurrentUser("USER");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);

    renderTicketDetailPage();

    expect(await screen.findByRole("heading", { name: "Invoice data not saved" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "编辑工单" })).not.toBeInTheDocument();
    expect(screen.queryByText("修改状态")).not.toBeInTheDocument();
    expect(screen.queryByText("分配处理人")).not.toBeInTheDocument();
    expect(screen.queryByText("查看操作日志")).not.toBeInTheDocument();
  });

  it("shows staff ticket actions and operation logs", async () => {
    setCurrentUser("STAFF");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);

    renderTicketDetailPage();

    expect(await screen.findByRole("button", { name: "编辑工单" })).toBeInTheDocument();
    expect(screen.getByText("修改状态")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "分配处理人" })).toBeInTheDocument();
    expect(screen.getByText("查看操作日志")).toBeInTheDocument();
  });

  it("loads current ticket logs from the ticket detail page", async () => {
    setCurrentUser("STAFF");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);
    const logsMock = vi.spyOn(api, "getTicketLogs").mockResolvedValue({
      records: [
        {
          id: 7,
          ticketId: 3,
          operatorId: 2,
          operatorName: "Staff One",
          action: "UPDATE_TICKET_STATUS",
          detail: "Ticket #3 changed to PROCESSING",
          createdAt: "2026-06-23T11:00:00",
        },
      ],
      total: 1,
      page: 1,
      size: 5,
    });

    renderTicketDetailPage();

    await userEvent.click(await screen.findByRole("button", { name: "查看当前工单日志" }));

    await waitFor(() => expect(logsMock).toHaveBeenCalledWith(3, { page: 1, size: 5 }));
    expect(await screen.findByText("UPDATE_TICKET_STATUS")).toBeInTheDocument();
    expect(screen.getByText("Ticket #3 changed to PROCESSING")).toBeInTheDocument();
  });

  it("shows forbidden message when current ticket logs are not allowed", async () => {
    setCurrentUser("STAFF");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);
    vi.spyOn(api, "getTicketLogs").mockRejectedValue(new ApiError("你没有权限查看操作日志", 403, 403));

    renderTicketDetailPage();

    expect(await screen.findByRole("heading", { name: "Invoice data not saved" })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "查看当前工单日志" }));

    expect(await screen.findByText("你没有权限查看操作日志")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Invoice data not saved" })).toBeInTheDocument();
  });

  it("shows admin-only detail actions", async () => {
    setCurrentUser("ADMIN");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue(ticketDetail);

    renderTicketDetailPage();

    expect(await screen.findByText("查看操作日志")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "删除" })).toBeInTheDocument();
    expect(screen.getByText("修改状态")).toBeInTheDocument();
  });

  it("lets admin assign and clear the ticket assignee from the detail page", async () => {
    localStorage.setItem(languageStorageKey, "en");
    setCurrentUser("ADMIN");
    vi.spyOn(api, "getTicketDetail").mockResolvedValue({
      ...ticketDetail,
      ticket: {
        ...ticketDetail.ticket,
        assignedTo: 2,
        assignedUserName: "Staff One",
      },
    });
    vi.spyOn(api, "getUsers").mockResolvedValue([
      { id: 1, username: "tom", name: "Tom", email: "tom@example.com", role: "USER" },
      { id: 2, username: "staff01", name: "Staff One", email: "staff01@example.com", role: "STAFF" },
      { id: 3, username: "admin01", name: "Admin One", email: "admin01@example.com", role: "ADMIN" },
    ]);
    const assignMock = vi.spyOn(api, "updateTicketAssignee").mockResolvedValue({
      ...ticketDetail.ticket,
      assignedTo: 3,
      assignedUserName: "Admin One",
    });

    renderTicketDetailPage();

    const assigneeSelect = await screen.findByRole("combobox", { name: "Assignee" });
    expect(within(assigneeSelect).queryByRole("option", { name: /Tom/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assign Ticket" })).not.toBeInTheDocument();

    await userEvent.selectOptions(assigneeSelect, "3");
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith(3, 3));

    await userEvent.click(screen.getByRole("button", { name: "Clear assignee" }));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith(3, null));
  });

  it("renders six AI action buttons", () => {
    renderPanel();

    expect(screen.getByRole("button", { name: "生成回复建议" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "总结当前工单" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "优先级建议" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "分类建议" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "相似工单" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "SLA 风险" })).toBeInTheDocument();
  });

  it("calls aiChat with message and a stable conversationId only", async () => {
    stubStableCrypto();
    const aiChatMock = vi.spyOn(api, "aiChat").mockResolvedValue(replySuggestionResponse());

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole("button", { name: "总结当前工单" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(2));

    expect(aiChatMock.mock.calls[0]).toEqual(["给 3 号工单生成回复建议", "detail-conversation"]);
    expect(aiChatMock.mock.calls[1]).toEqual(["总结 3 号工单", "detail-conversation"]);
    expect(aiChatMock.mock.calls[0]).toHaveLength(2);
    expect(aiChatMock.mock.calls[1]).toHaveLength(2);
  });

  it("renders all structured AI result cards", async () => {
    vi.spyOn(api, "aiChat")
      .mockResolvedValueOnce(replySuggestionResponse())
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          summary: "该工单涉及发票字段刷新后丢失。",
          key_points: ["发票数据未保存", "需要排查保存链路"],
          risk_flags: [],
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          suggested_priority: "HIGH",
          confidence: 0.76,
          reason: "影响报销流程。",
          risk_flags: riskFlags,
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          suggested_category: "发票财务",
          confidence: 0.81,
          reason: "标题涉及发票。",
          risk_flags: [],
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          similar_tickets: [
            {
              id: 12,
              title: "发票字段消失",
              status: "CLOSED",
              similarity_reason: "都涉及发票数据保存失败。",
            },
          ],
          risk_flags: [],
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          sla_risk_level: "MEDIUM",
          reason: "紧急工单仍未关闭。",
          missing_fields: ["resolveDueAt"],
          risk_flags: ["SLA字段不足"],
        },
      });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    expect(await screen.findByDisplayValue(originalSuggestion)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "总结当前工单" }));
    expect(await screen.findByText("该工单涉及发票字段刷新后丢失。")).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "优先级建议" }));
    expectTextContent("建议优先级：高");

    await userEvent.click(screen.getByRole("button", { name: "分类建议" }));
    expectTextContent("建议分类：发票财务");

    await userEvent.click(screen.getByRole("button", { name: "相似工单" }));
    expect(await screen.findByText(/#12/)).toBeInTheDocument();
    expect(screen.getByText(/都涉及发票数据保存失败/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "SLA 风险" }));
    expectTextContent("SLA 风险等级：MEDIUM");
    expect(screen.getByText("resolveDueAt")).toBeInTheDocument();
  });

  it("does not show save or apply actions to USER AI result cards", async () => {
    setCurrentUser("USER");
    vi.spyOn(api, "aiChat").mockResolvedValueOnce(replySuggestionResponse());

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));

    expect(await screen.findByDisplayValue(originalSuggestion)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "保存 AI 回复" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "采纳分类" })).not.toBeInTheDocument();
  });

  it.each([
    ["STAFF", "staff-token"],
    ["ADMIN", "admin-token"],
  ] as const)("submits the edited AI reply suggestion for %s", async (role, token) => {
    stubStableCrypto();
    useAuthStore.getState().setSession(token, {
      id: role === "ADMIN" ? 3 : 2,
      username: role === "ADMIN" ? "admin01" : "staff01",
      name: role === "ADMIN" ? "Admin One" : "Staff One",
      email: role === "ADMIN" ? "admin01@example.com" : "staff01@example.com",
      role,
    });
    vi.spyOn(api, "aiChat").mockResolvedValueOnce(replySuggestionResponse());
    const pendingMock = vi.spyOn(api, "createAiReplyPending").mockResolvedValue({
      type: "PENDING_CONFIRMATION",
      message: "Please confirm saving this AI reply.",
      data: {
        actionType: "SAVE_AI_REPLY",
        payload: { ticketId: 3, replyContent: editedSuggestion },
      },
      risk_flags: riskFlags,
    });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    const draft = await screen.findByDisplayValue(originalSuggestion);
    await userEvent.clear(draft);
    await userEvent.type(draft, editedSuggestion);
    await userEvent.click(screen.getByRole("button", { name: "保存 AI 回复" }));

    await waitFor(() =>
      expect(pendingMock).toHaveBeenCalledWith(
        3,
        expect.objectContaining({
          conversationId: "detail-conversation",
          suggestion: editedSuggestion,
          originalSuggestion,
          confidence: 0.82,
          reason: suggestionReason,
          riskFlags,
        }),
      ),
    );
    expect(await screen.findByText("Please confirm saving this AI reply.")).toBeInTheDocument();
  });

  it("shows apply category action to staff and creates a pending action", async () => {
    stubStableCrypto();
    setCurrentUser("STAFF");
    vi.spyOn(api, "aiChat").mockResolvedValueOnce({
      type: "JSON_RESULT",
      data: {
        suggested_category: "发票财务",
        confidence: 0.81,
        reason: "标题涉及发票。",
        risk_flags: [],
      },
    });
    const pendingMock = vi.spyOn(api, "createCategoryPending").mockResolvedValue({
      type: "PENDING_CONFIRMATION",
      message: "请确认是否将该工单分类更新为：发票财务。",
      data: {
        actionType: "APPLY_AI_CATEGORY",
        payload: { ticketId: 3, category: "发票财务" },
      },
      risk_flags: riskFlags,
    });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "分类建议" }));
    await userEvent.click(await screen.findByRole("button", { name: "采纳分类" }));

    await waitFor(() =>
      expect(pendingMock).toHaveBeenCalledWith(3, {
        conversationId: "detail-conversation",
        category: "发票财务",
        confidence: 0.81,
        reason: "标题涉及发票。",
      }),
    );
    expect(await screen.findByText("待确认操作")).toBeInTheDocument();
  });

  it("does not show write actions for priority or SLA result cards", async () => {
    setCurrentUser("STAFF");
    vi.spyOn(api, "aiChat")
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          suggested_priority: "HIGH",
          confidence: 0.76,
          reason: "影响报销流程。",
          risk_flags: riskFlags,
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          sla_risk_level: "MEDIUM",
          reason: "紧急工单仍未关闭。",
          missing_fields: ["resolveDueAt"],
          risk_flags: ["SLA字段不足"],
        },
      });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "优先级建议" }));
    expect(await screen.findByText("影响报销流程。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "保存 AI 回复" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "采纳分类" })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "SLA 风险" }));
    expect(await screen.findByText("紧急工单仍未关闭。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "保存 AI 回复" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "采纳分类" })).not.toBeInTheDocument();
  });

  it("keeps forbidden results without clearing auth", async () => {
    vi.spyOn(api, "aiChat").mockRejectedValue(new ApiError("你没有权限执行该操作。", 403, 403));
    setCurrentUser("USER");

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));

    expect(await screen.findByText("权限不足")).toBeInTheDocument();
    expect(screen.getByText("你没有权限执行该操作。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重试" })).not.toBeInTheDocument();
    expect(useAuthStore.getState().token).toBe("user-token");
  });

  it("shows errors with retry and reuses the original message and conversationId", async () => {
    stubStableCrypto();
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockRejectedValueOnce(new ApiError("服务暂时异常，请稍后重试。", 500, 500))
      .mockResolvedValueOnce({
        type: "NORMAL",
        message: "重试成功",
        data: null,
      });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));

    expect(await screen.findByText("请求失败")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重试" }));

    expect(await screen.findByText("重试成功")).toBeInTheDocument();
    expect(aiChatMock.mock.calls[1]).toEqual(["给 3 号工单生成回复建议", "detail-conversation"]);
  });

  it("renders pending actions and confirms or cancels with the current conversationId", async () => {
    stubStableCrypto();
    const invalidateSpy = vi.spyOn(QueryClient.prototype, "invalidateQueries");
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockResolvedValueOnce({
        type: "PENDING_CONFIRMATION",
        message: "Please confirm saving this AI reply.",
        data: {
          actionType: "SAVE_AI_REPLY",
          payload: { ticketId: 3, replyContent: "AI suggested reply" },
        },
        risk_flags: ["needs human review"],
      })
      .mockResolvedValueOnce({
        type: "NORMAL",
        message: "cancelled",
        data: null,
      })
      .mockResolvedValueOnce({
        type: "PENDING_CONFIRMATION",
        message: "Please confirm saving this AI reply.",
        data: {
          actionType: "SAVE_AI_REPLY",
          payload: { ticketId: 3, replyContent: "AI suggested reply" },
        },
        risk_flags: ["needs human review"],
      })
      .mockResolvedValueOnce({
        type: "NORMAL",
        message: "confirmed",
        data: null,
      });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));

    expect(await screen.findByText("Please confirm saving this AI reply.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "取消操作" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(2));

    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    expect(await screen.findByText("Please confirm saving this AI reply.")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "确认执行" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(4));

    expect(aiChatMock.mock.calls[1]).toEqual(["取消", "detail-conversation"]);
    expect(aiChatMock.mock.calls[3]).toEqual(["确认", "detail-conversation"]);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["ticket-detail", 3] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["ticket-logs", 3] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["tickets"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["logs"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["admin-dashboard-stats"] });
  });

  it("clears local AI drafts after confirming a saved AI reply", async () => {
    stubStableCrypto();
    setCurrentUser("STAFF");
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockResolvedValueOnce(replySuggestionResponse())
      .mockResolvedValueOnce({
        type: "NORMAL",
        message: "confirmed",
        data: null,
      });
    vi.spyOn(api, "createAiReplyPending").mockResolvedValue({
      type: "PENDING_CONFIRMATION",
      message: "Please confirm saving this AI reply.",
      data: {
        actionType: "SAVE_AI_REPLY",
        payload: { ticketId: 3, replyContent: editedSuggestion },
      },
      risk_flags: riskFlags,
    });

    renderPanel();
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    const draft = await screen.findByDisplayValue(originalSuggestion);
    await userEvent.clear(draft);
    await userEvent.type(draft, editedSuggestion);
    await userEvent.click(screen.getByRole("button", { name: "保存 AI 回复" }));
    await userEvent.click(await screen.findByRole("button", { name: "确认执行" }));

    await waitFor(() => expect(aiChatMock).toHaveBeenCalledWith("确认", "detail-conversation"));
    await waitFor(() => expect(screen.queryByDisplayValue(editedSuggestion)).not.toBeInTheDocument());
    expect(screen.queryByText("Please confirm saving this AI reply.")).not.toBeInTheDocument();
  });

  it("keeps the same conversationId after language switching", async () => {
    stubStableCrypto();
    const aiChatMock = vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "NORMAL",
      message: "ok",
      data: null,
    });

    renderPanel({ withLanguageSwitcher: true });
    await userEvent.click(screen.getByRole("button", { name: "生成回复建议" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole("button", { name: "语言" }));
    expect(localStorage.getItem(languageStorageKey)).toBe("en");

    await userEvent.click(screen.getByRole("button", { name: "Summarize Ticket" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(2));

    expect(aiChatMock.mock.calls[0][1]).toBe("detail-conversation");
    expect(aiChatMock.mock.calls[1][1]).toBe("detail-conversation");
    expect(within(screen.getByRole("button", { name: "Language" })).getByText("中文")).toBeInTheDocument();
  });
});

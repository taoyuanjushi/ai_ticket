import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { ApiError } from "../api/http";
import { LanguageSwitcher } from "../components/LanguageSwitcher";
import { I18nProvider, languageStorageKey } from "../i18n";
import { useAuthStore } from "../state/authStore";
import { AiPage } from "./AiPage";

function renderAiPage({ withLanguageSwitcher = false } = {}) {
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
        <AiPage />
      </I18nProvider>
    </QueryClientProvider>,
  );
}

function expectTextContent(text: string) {
  expect(
    screen.getByText((_, node) => {
      if (!node) {
        return false;
      }

      return node.textContent === text;
    }),
  ).toBeInTheDocument();
}

describe("AiPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.removeItem(languageStorageKey);
    useAuthStore.getState().clearSession();
  });

  it("renders normal answers as chat history without confirm controls", async () => {
    vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "NORMAL",
      message: "你当前有 3 个工单。",
      data: null,
      risk_flags: [],
    });

    renderAiPage();

    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("List current processing tickets")).length).toBeGreaterThan(0);
    expect(screen.getByText("你当前有 3 个工单。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "确认执行" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "取消操作" })).not.toBeInTheDocument();
  });

  it("renders pending action card with confirm and cancel", async () => {
    vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "PENDING_CONFIRMATION",
      message: "请确认是否创建工单",
      data: {
        actionType: "CREATE_TICKET",
        payload: {
          title: "数据库连接失败",
          description: "测试环境偶发无法连接",
          priority: "HIGH",
        },
      },
      risk_flags: ["需要人工确认"],
    });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("待确认操作")).length).toBeGreaterThan(0);
    expect(screen.getByText("创建工单")).toBeInTheDocument();
    expect(screen.getByText("数据库连接失败")).toBeInTheDocument();
    expect(screen.getByText("需要人工确认")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /确认执行/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /取消操作/ })).toBeInTheDocument();
  });

  it("uses the same conversationId for confirm and cancel and keeps token after language switch", async () => {
    let uuidCount = 0;
    vi.stubGlobal("crypto", {
      randomUUID: () => (uuidCount++ === 0 ? "conversation-fixed" : `msg-${uuidCount}`),
    });
    const aiChatMock = vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "PENDING_CONFIRMATION",
      message: "请确认是否创建工单",
      data: {
        actionType: "CREATE_TICKET",
        payload: { title: "测试工单" },
      },
      risk_flags: [],
    });
    useAuthStore.getState().setSession("token-before-switch", {
      id: 1,
      username: "staff01",
      name: "Staff One",
      email: "staff01@example.com",
      role: "STAFF",
    });

    renderAiPage({ withLanguageSwitcher: true });

    await userEvent.click(screen.getByRole("button", { name: "发送" }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole("button", { name: "语言" }));
    expect(localStorage.getItem(languageStorageKey)).toBe("en");
    expect(useAuthStore.getState().token).toBe("token-before-switch");

    await userEvent.click(screen.getByRole("button", { name: /Confirm/ }));
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(2));

    await userEvent.click(screen.getAllByRole("button", { name: /Cancel/ })[0]);
    await waitFor(() => expect(aiChatMock).toHaveBeenCalledTimes(3));

    expect(aiChatMock.mock.calls[1]).toEqual(["确认", "conversation-fixed"]);
    expect(aiChatMock.mock.calls[2]).toEqual(["取消", "conversation-fixed"]);
    expect(aiChatMock.mock.calls[1]).toHaveLength(2);
  });

  it("renders forbidden card without clearing auth state", async () => {
    vi.spyOn(api, "aiChat").mockRejectedValue(new ApiError("你没有权限执行该操作。", 403, 403));
    useAuthStore.getState().setSession("token-kept", {
      id: 1,
      username: "tom",
      name: "Tom",
      email: "tom@example.com",
      role: "USER",
    });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("权限不足")).toBeInTheDocument();
    expect(screen.getByText("你没有权限执行该操作。")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重试" })).not.toBeInTheDocument();
    expect(useAuthStore.getState().token).toBe("token-kept");
  });

  it("renders errors with retry and resends the previous user message", async () => {
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockRejectedValueOnce(new ApiError("服务暂时异常，请稍后重试。", 500, 500))
      .mockResolvedValueOnce({
        type: "NORMAL",
        message: "重试成功",
        data: null,
        risk_flags: [],
      });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("请求失败")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "重试" }));

    expect(await screen.findByText("重试成功")).toBeInTheDocument();
    expect(aiChatMock.mock.calls[1][0]).toBe("List current processing tickets");
  });

  it("renders reply suggestion JSON card without legacy next_steps", async () => {
    vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "JSON_RESULT",
      message: "回复建议生成完成",
      data: {
        suggestion: "建议先确认报错时间和截图。",
        confidence: 0.78,
        reason: "工单描述缺少错误细节。",
        risk_flags: ["信息不足", "需要人工确认"],
        next_steps: ["legacy"],
      },
      risk_flags: ["信息不足"],
    });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("回复建议")).length).toBeGreaterThan(0);
    expect(screen.getByText("建议先确认报错时间和截图。")).toBeInTheDocument();
    expectTextContent("置信度：78%");
    expectTextContent("依据：工单描述缺少错误细节。");
    expect(screen.getByText("信息不足")).toBeInTheDocument();
    expect(screen.queryByText("legacy")).not.toBeInTheDocument();
    expect(screen.queryByText("next_steps")).not.toBeInTheDocument();
  });

  it("renders summary JSON card", async () => {
    vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "JSON_RESULT",
      data: {
        summary: "该工单需要继续跟进登录失败问题。",
        key_points: ["用户无法登录", "需要补充截图"],
        risk_flags: ["信息不足"],
      },
    });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("总结工单")).length).toBeGreaterThan(0);
    expectTextContent("摘要：该工单需要继续跟进登录失败问题。");
    expect(screen.getByText("用户无法登录")).toBeInTheDocument();
  });

  it("renders priority and category JSON cards", async () => {
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          suggested_priority: "HIGH",
          confidence: 0.8,
          reason: "影响登录。",
          risk_flags: [],
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          suggested_category: "账号登录",
          confidence: 0.7,
          reason: "描述包含登录关键词。",
          risk_flags: [],
        },
      });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("优先级建议")).length).toBeGreaterThan(0);
    expectTextContent("建议优先级：高");
    expectTextContent("依据：影响登录。");

    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("分类建议")).length).toBeGreaterThan(0);
    expectTextContent("建议分类：账号登录");
    expect(aiChatMock).toHaveBeenCalledTimes(2);
  });

  it("renders SLA risk JSON card", async () => {
    vi.spyOn(api, "aiChat").mockResolvedValue({
      type: "JSON_RESULT",
      data: {
        sla_risk_level: "MEDIUM",
        reason: "高优先级工单仍未关闭。",
        missing_fields: ["resolveDueAt"],
        risk_flags: ["SLA字段不足"],
      },
    });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("SLA 风险")).length).toBeGreaterThan(0);
    expectTextContent("SLA 风险等级：MEDIUM");
    expect(screen.getByText("resolveDueAt")).toBeInTheDocument();
    expect(screen.getByText("SLA字段不足")).toBeInTheDocument();
  });

  it("renders similar tickets list and empty state", async () => {
    const aiChatMock = vi
      .spyOn(api, "aiChat")
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          similar_tickets: [
            {
              id: 12,
              title: "登录接口报错",
              status: "CLOSED",
              similarity_reason: "都涉及登录失败",
            },
          ],
          risk_flags: [],
        },
      })
      .mockResolvedValueOnce({
        type: "JSON_RESULT",
        data: {
          similar_tickets: [],
          risk_flags: [],
        },
      });

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("相似工单")).length).toBeGreaterThan(0);
    expect(screen.getByText(/#12/)).toBeInTheDocument();
    expect(screen.getByText(/都涉及登录失败/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect(await screen.findByText("未找到相似工单")).toBeInTheDocument();
    expect(aiChatMock).toHaveBeenCalledTimes(2);
  });

  it("shows thinking loading state while request is pending", async () => {
    let resolveRequest: (value: Awaited<ReturnType<typeof api.aiChat>>) => void = () => undefined;
    vi.spyOn(api, "aiChat").mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }) as ReturnType<typeof api.aiChat>,
    );

    renderAiPage();
    await userEvent.click(screen.getByRole("button", { name: "发送" }));

    expect((await screen.findAllByText("AI 正在思考...")).length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "AI 正在思考..." })).toBeDisabled();

    resolveRequest({ type: "NORMAL", message: "完成", data: null });
    expect(await screen.findByText("完成")).toBeInTheDocument();
  });
});

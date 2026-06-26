# Python Agent 分层整理

本阶段目标不是重写 Agent，而是把主链路职责说清楚、入口变薄、响应变统一。

## 当前主链路

```text
FastAPI /agent/chat
-> AgentToolService.handle(request)
-> IntentRecognizer.recognize(message)
-> AgentToolService 按 IntentResult 分发
-> JavaTicketClient / TicketAiService / capability services
-> ResponseBuilder 生成 AgentResponse
-> API 层转换为兼容前端的 AgentChatResponse
```

## 分层职责

| 层 | 负责什么 | 不负责什么 |
| --- | --- | --- |
| `app/api/agent_api.py` | 接收请求、调用 `AgentToolService.handle()`、把 `AgentResponse` 转成旧前端兼容响应 | 不识别意图、不直接调 Java、不拼业务分支 |
| `IntentRecognizer` | 把自然语言解析成 `IntentResult`，提取 `ticket_id/title/priority/status` 等字段 | 不调用 Java、不调用 LLM、不创建 pending_action |
| `AgentToolService` | 编排主流程，按意图分发到查询、pending_action、确认取消、AI 能力服务 | 不直接写数据库，不保存真实 pending_action |
| `JavaTicketClient` | 调 Java REST API，统一处理 401/403/404/500、连接失败、超时、非 JSON 响应 | 不决定业务意图，不构造最终前端展示 |
| `TicketAiService` 和 `services/ai_capabilities/*` | 回复建议、摘要、优先级、分类、相似工单、SLA 风险等 AI 能力 | 不绕过 Java 权限，不直接写库 |
| `TicketGroundingService` | 获取或整理真实工单上下文，处理 Java 权限失败，生成信息不足降级结果 | 不做 HTTP 路由，不保存 pending_action |
| `GuardrailService` | 只检查模型输出中是否出现无依据高风险结论 | 不调用 Java、不调用 LLM、不构造 API 响应 |
| `LLMJsonParser` | 把 LLM 文本解析成 JSON dict | 不做业务判断，不决定 response type |
| `ResponseBuilder` | 统一生成 `NORMAL/JSON_RESULT/MISSING_FIELDS/PENDING_CONFIRMATION/ERROR` 等 `AgentResponse` | 不识别意图，不调用外部服务 |

## 写操作边界

写操作仍然必须 Human-in-the-loop：

```text
创建工单 / 修改状态 / 保存 AI 回复
-> Python 只创建 Java pending_action
-> 用户确认
-> Python 使用当前 auth_token 调 Java confirm
-> Java 校验当前用户和 conversationId
-> Java 执行业务写入
```

`pending_action` 不保存在 Python 全局内存中，也不保存 token。Python 当前只保留开发用 `pending_intent`，用于多轮追问时暂存缺失字段。

## 为什么 API 层要薄

API 层越薄，越容易测试：

1. API 测试只需要证明它把 `AgentChatRequest` 交给 `AgentToolService.handle()`。
2. 意图识别测试只关心自然语言能否转成 `IntentResult`。
3. 分发测试只关心不同 intent 是否进入正确 service。
4. Java 调用测试只关心 HTTP、token、错误映射。
5. 响应测试只关心 `AgentResponse` 结构是否稳定。

这样以后重构某一层时，不容易把其他层一起改坏。

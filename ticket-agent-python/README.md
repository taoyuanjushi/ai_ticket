# ticket-agent-python

`ticket-agent-python` 是一个用于学习 AI Agent 应用开发的 Python 项目。当前阶段在已有 FastAPI Agent 服务基础上，引入 LangChain Tool 的最小实践：实现工具定义、工具注册、工具列表接口，以及 `/agent/chat` 中的简单规则路由。

当前工单工具的数据源已切换为 Java Spring Boot REST API。Python 不直接连接 MySQL，不维护真实工单数据；写操作先在 Java 后端创建 `pending_action`，用户确认后才由 Java 执行业务写入。Python 侧只保留开发用 `pending_intent`，用于多轮追问时暂存“还没补齐的参数”。

## 当前阶段目标

- 学习 FastAPI 项目结构。
- 使用 Pydantic 定义请求和响应模型。
- 了解如何调用兼容 OpenAI Chat Completions 风格的 LLM API。
- 使用 `.env` 管理配置。
- 增加基础日志记录。
- 实现统一错误响应。
- 提供 health check 接口。
- 学习 LangChain Tool 的基础定义和调用方式。
- 使用 Java REST API 实现查询类 Tool。
- 使用 Java REST API 实现创建类 Tool。
- 使用 Java REST API 实现状态修改类 Tool。
- 学习写操作的 Human-in-the-loop 人工确认机制。
- 学习按用户或会话隔离 Human-in-the-loop 确认态。
- 基于 Java 工单详情调用 LLM 生成 AI 回复建议。
- 使用 `pending_intent` 支持创建工单、修改状态、回复建议等场景的多轮字段补齐。
- 编写基础接口测试。

## 项目结构

```text
ticket-agent-python/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── api/
│   │   ├── __init__.py
│   │   ├── agent_api.py
│   │   └── ticket_ai_api.py
│   ├── core/
│   │   ├── __init__.py
│   │   ├── config.py
│   │   ├── exceptions.py
│   │   └── logger.py
│   ├── clients/
│   │   ├── __init__.py
│   │   ├── java_ticket_client.py
│   │   └── llm_client.py
│   ├── prompts/
│   │   ├── __init__.py
│   │   └── reply_suggestion_prompt.py
│   ├── schemas/
│   │   ├── __init__.py
│   │   ├── agent_state_schema.py
│   │   ├── agent_schema.py
│   │   ├── ticket_tool_schema.py
│   │   ├── ticket_ai_schema.py
│   │   └── tool_schema.py
│   ├── services/
│   │   ├── __init__.py
│   │   ├── agent_state_service.py
│   │   ├── agent_tool_service.py
│   │   ├── llm_service.py
│   │   ├── pending_action_store.py
│   │   └── ticket_ai_service.py
│   └── tools/
│       ├── __init__.py
│       ├── capability_tools.py
│       ├── mock_ticket_data.py
│       ├── ticket_tools.py
│       └── tool_registry.py
├── tests/
│   ├── __init__.py
│   ├── test_agent_api.py
│   ├── test_agent_tools.py
│   ├── test_create_ticket_tool.py
│   ├── test_human_confirmation.py
│   ├── test_search_tickets_tool.py
│   └── test_update_ticket_status_tool.py
├── .env.example
├── .gitignore
├── README.md
└── requirements.txt
```

## 创建虚拟环境

```bash
python -m venv .venv
```

Windows PowerShell:

```powershell
.\.venv\Scripts\Activate.ps1
```

macOS 或 Linux:

```bash
source .venv/bin/activate
```

## 安装依赖

```bash
pip install -r requirements.txt
```

如果当前机器默认 Python 版本较旧，Windows 可以显式使用 Python 3.12：

```powershell
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

## 配置 `.env`

先复制示例配置：

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env` 示例：

```env
APP_NAME=ticket-agent-python
APP_ENV=dev
LOG_LEVEL=INFO

LLM_API_KEY=
LLM_PROVIDER=openai_compatible
LLM_API_BASE_URL=
LLM_BASE_URL=
LLM_MODEL=
LLM_TIMEOUT=30
LLM_TIMEOUT_SECONDS=30
LLM_MOCK_MODE=true

JAVA_API_BASE_URL=http://127.0.0.1:8080
JAVA_API_TOKEN=
JAVA_API_TIMEOUT=10
```

不要提交真实 `.env` 文件，也不要把 API Key 写死在代码中。

LLM 配置说明：

- `LLM_PROVIDER`：LLM 服务类型，当前使用 `openai_compatible`。
- `LLM_API_KEY`：真实 LLM API Key，不能提交到代码仓库。
- `LLM_API_BASE_URL`：真实 LLM 服务地址，例如兼容 OpenAI 的 `/v1` 地址。
- `LLM_MODEL`：真实 LLM 模型名。
- `LLM_TIMEOUT_SECONDS`：AI 回复建议调用 LLM 的超时时间。
- `LLM_MOCK_MODE`：为 `true` 时不调用真实 LLM，直接返回本地 mock 回复建议。
- `LLM_BASE_URL` / `LLM_TIMEOUT`：保留给已有普通聊天 fallback 使用；新代码优先使用 `LLM_API_BASE_URL` / `LLM_TIMEOUT_SECONDS`。

Java API 配置说明：

- `JAVA_API_BASE_URL`：Java Spring Boot 后端地址，默认 `http://127.0.0.1:8080`。
- `JAVA_API_TOKEN`：本地测试时可选，用于调用需要 JWT 的 Java 接口。
- `JAVA_API_TIMEOUT`：Python 调 Java API 的超时时间，默认 10 秒。
- `/agent/chat` 请求体里的 `auth_token` 优先级高于 `.env` 中的 `JAVA_API_TOKEN`。

## 多轮追问：pending_intent 与 pending_action

当前 Agent 有两类状态，含义不同：

| 状态 | 保存位置 | 作用 | 是否写数据库 |
| --- | --- | --- | --- |
| `pending_intent` | Python 开发用内存 Store | 暂存缺字段的意图和已收集参数 | 不会 |
| `pending_action` | Java 后端 | 暂存参数完整、等待用户确认的写操作 | 确认后会 |

`pending_intent` 只保存这些业务字段：

```json
{
  "user_id": "7",
  "conversation_id": "chat-001",
  "intent": "CREATE_TICKET",
  "collected": {
    "priority": "HIGH"
  },
  "missing_fields": ["title", "description"],
  "created_at": "2026-06-23T10:00:00Z",
  "updated_at": "2026-06-23T10:00:00Z"
}
```

它不会保存 `auth_token`、`Authorization`、JWT 或密码。隔离 key 为：

```text
ai:pending_intent:{user_id}:{conversation_id}
```

默认 10 分钟过期。它按 `user_id + conversation_id` 隔离，避免用户 A 的补充信息误补到用户 B 的会话，也避免同一用户多个聊天窗口互相覆盖。

创建工单多轮追问示例：

```text
用户：帮我创建一个高优先级工单
AI：请补充工单标题和描述。
用户：标题是数据库连接失败，描述是测试环境偶发无法连接
AI：请确认是否创建该工单。
用户：确认
AI：已创建工单：ID 6，标题：数据库连接失败，优先级 HIGH，状态 OPEN。
```

修改状态多轮追问示例：

```text
用户：把工单改成处理中
AI：修改工单状态还缺少必要信息：工单 ID。请说明要修改几号工单。
用户：3 号
AI：请确认是否修改该工单状态。
用户：确认
AI：已将 3 号工单状态修改为 PROCESSING。
```

取消规则：

```text
用户：取消
AI：已取消当前待补充或待确认的操作。
```

取消会清理当前 `pending_intent`，并调用 Java 当前会话的 `pending_action` 取消逻辑；不会执行创建、修改状态或保存回复建议。

字段补齐后仍然需要人工确认，因为创建工单、修改状态、保存 AI 回复建议都属于写操作。Python 只能编排意图和参数，真正写入必须由 Java 在当前有效 `auth_token` 下完成权限校验后执行。

运行 Python 回归测试：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

## AI 结构化输出

`/agent/chat` 面向前端统一返回 `AgentResponse` 外层结构。普通文字回答使用 `NORMAL`，AI 分析成功使用 `JSON_RESULT`，缺字段使用 `MISSING_FIELDS`，写操作确认使用 `PENDING_CONFIRMATION`，登录失效使用 `UNAUTHORIZED`，无权限或不可访问使用 `FORBIDDEN`，异常使用 `ERROR`。

统一外层格式：

```json
{
  "type": "JSON_RESULT",
  "message": "分析完成",
  "data": {},
  "risk_flags": []
}
```

当前结构化能力的 `data` 固定为：

| 能力 | message | data 字段 |
| --- | --- | --- |
| 回复建议 | 回复建议生成完成 | `suggestion`、`confidence`、`reason`、`risk_flags` |
| 工单摘要 | 工单摘要生成完成 | `summary`、`key_points`、`risk_flags` |
| 优先级建议 | 优先级建议生成完成 | `suggested_priority`、`confidence`、`reason`、`risk_flags` |
| 分类建议 | 分类建议生成完成 | `suggested_category`、`confidence`、`reason`、`risk_flags` |
| 相似工单 | 相似工单检索完成 | `similar_tickets`、`risk_flags` |
| SLA 风险 | SLA 风险分析完成 | `sla_risk_level`、`reason`、`missing_fields`、`risk_flags` |

示例：

```json
{
  "type": "JSON_RESULT",
  "message": "优先级建议生成完成",
  "data": {
    "suggested_priority": "HIGH",
    "confidence": 0.76,
    "reason": "工单描述中包含登录失败和阻塞等高影响关键词。",
    "risk_flags": []
  },
  "risk_flags": []
}
```

为什么不返回 Markdown：前端需要稳定解析字段并渲染卡片，Markdown 或混合自然语言容易导致页面只能按普通文本展示，也不方便测试。

为什么要用 Pydantic：`app/schemas/ai_outputs.py` 会校验 `confidence` 必须在 `0.0` 到 `1.0` 之间，优先级只能是 `LOW`、`MEDIUM`、`HIGH`，SLA 风险只能是 `LOW`、`MEDIUM`、`HIGH`、`UNKNOWN`，列表字段必须真的是 list。

LLM 非法 JSON 处理：`app/services/llm_json_parser.py` 会先解析纯 JSON；如果模型返回 Markdown 代码块或前后带解释文字，会尝试提取 JSON 对象再解析一次；仍失败时返回可读 `ERROR`，`risk_flags` 包含 `JSON解析失败`，不会把 Python 堆栈返回前端。

权限和 grounding 规则：所有基于工单详情的能力必须先通过 Java API 获取真实工单数据。Java 返回 401、403、404、500、连接失败或超时时，Python 不调用 LLM，不生成假结果，直接返回 `UNAUTHORIZED`、`FORBIDDEN` 或 `ERROR`。SLA 缺少 `deadline`、`responseDueAt`、`resolveDueAt` 等字段时，只能返回 `UNKNOWN`、`missing_fields` 和 `SLA字段不足`，不能编造“还有 2 小时超时”。

## Grounding 防幻觉规则

Grounding 的意思是：AI 结果必须“落地”到 Java 后端返回的真实工单详情上。当前必须 grounding 的能力包括：回复建议、工单摘要、优先级建议、分类建议、相似工单检索、SLA 风险提醒。

标准流程：

```text
用户：总结 3 号工单
-> Python 识别 TICKET_SUMMARY
-> Python 调 Java GET /tickets/3/detail
-> Java 做登录和权限校验
-> 200：Python 只基于 ticket_detail 生成结构化 JSON
-> 401/403/404/500/连接失败/超时：Python 直接返回错误，不调用 LLM
```

为什么 403 / 404 后不能调用 LLM：如果 Java 已经拒绝访问，Python 就没有可信上下文。继续让模型生成，会把“无权限或不存在”的工单伪造成有内容的工单，既有安全风险，也会误导用户。

`app/services/grounding.py` 负责统一处理：

- 判断哪些意图必须先读 Java 工单详情；
- 构造允许传给 LLM 的 `ticket_context`，只包含 `id`、`title`、`description`、`status`、`priority`、`category`、`assignedTo`、时间字段和历史回复内容；
- 移除 `token`、`authorization`、`password` 等敏感字段；
- 检测高风险表述，例如“已经修复”“日志显示”“监控显示”“根因是”“SLA 已超时”“还有 2 小时超时”；
- 如果工单详情中没有依据，在 `risk_flags` 中加入 `可能包含未依据结论`，必要时降级为“信息不足”。

SLA 规则：如果 Java 详情缺少 `deadline`、`responseDueAt`、`resolveDueAt`、`createdAt`、`updatedAt`、`status` 等字段，就返回 `sla_risk_level=UNKNOWN`，并列出 `missing_fields`。没有截止时间时，不能说“已超时”或“还有多久超时”。

相似工单规则：先读取目标工单详情，再通过 Java `/tickets` 查询候选工单。候选只来自 Java 返回的当前用户可访问范围，Python 不直连数据库，也不从本地未授权缓存拿数据。

运行 Python 回归测试：

```powershell
cd ticket-agent-python
.\.venv\Scripts\python.exe -m pytest
```

## 第二阶段：工具从 mock 数据切换到 Java API

当前工具数据源：

```text
search_tickets -> Java GET /tickets
create_ticket -> 确认后调用 Java POST /tickets
update_ticket_status -> 确认后调用 Java PUT /tickets/{id}/status
```

运行顺序：

```text
1. 启动 Java Spring Boot 后端
2. 登录 Java 获取 JWT Token
3. 启动 Python AI 服务
4. 请求 Python /agent/chat，并传入 auth_token
5. Python 工具调用 Java REST API
```

登录 Java 获取 Token：

```http
POST http://127.0.0.1:8080/auth/login
Content-Type: application/json
```

```json
{
  "username": "tom",
  "password": "123456"
}
```

调用 Python Agent 时透传 Token：

```json
{
  "message": "查一下所有处理中工单",
  "user_id": "1",
  "conversation_id": "chat-001",
  "auth_token": "从 Java 登录接口拿到的 JWT Token"
}
```

## 第三阶段：确认态隔离

为了避免多个用户或多个会话共用同一个状态，`/agent/chat` 请求体使用 `user_id` 和 `conversation_id` 做隔离。当前实现中：

- `pending_intent`：Python 开发用内存 Store，保存缺字段的临时意图。
- `pending_action`：Java 后端托管，保存参数完整、等待确认的写操作。
- 确认和取消都使用当前请求携带的 `auth_token`，不会使用旧 token。

请求体格式：

```json
{
  "message": "把 3 号工单改成处理中",
  "user_id": "1",
  "conversation_id": "chat-001",
  "auth_token": "JWT_TOKEN"
}
```

字段说明：

- `message`：用户自然语言输入，必填，不能为空。
- `user_id`：当前用户 ID，可选，但正式使用时应传入。
- `conversation_id`：当前会话 ID，可选；如果传入，会优先用于隔离确认态。
- `auth_token`：调用 Java API 时使用的 JWT Token，可选。

`pending_intent` key 生成规则：

```text
ai:pending_intent:{user_id}:{conversation_id}
```

如果没有传入 `user_id`，Python 会使用 `anonymous` 作为本地开发兜底；正式链路应由 Java 转发真实用户身份和会话 ID。

Java `pending_action` 的业务 payload 示例：

```json
{
  "actionType": "UPDATE_TICKET_STATUS",
  "payload": {
    "ticketId": 3,
    "status": "PROCESSING"
  }
}
```

Java 托管 `pending_action` 的隔离维度：

```text
userId + conversationId
```

Python 不保存 `pending_action` token，不直接写数据库，也不绕过 Java 权限。确认成功后由 Java 修改数据库；重复确认不能重复执行。

Postman 测试流程：

1. 创建 pending action。

```http
POST http://127.0.0.1:8001/agent/chat
Content-Type: application/json
```

```json
{
  "user_id": "1",
  "conversation_id": "chat-001",
  "message": "把 3 号工单改成处理中",
  "auth_token": "从 Java 登录接口获取的 JWT"
}
```

预期返回确认提示，不会立即修改 Java 工单。

2. 确认执行。

```json
{
  "user_id": "1",
  "conversation_id": "chat-001",
  "message": "确认",
  "auth_token": "从 Java 登录接口获取的 JWT"
}
```

预期只执行 `chat-001` 对应的 `pending_action`。

3. 取消执行。

```json
{
  "user_id": "1",
  "conversation_id": "chat-001",
  "message": "取消",
  "auth_token": "从 Java 登录接口获取的 JWT"
}
```

预期只取消 `chat-001` 对应的 `pending_action`。

4. 其他会话确认。

```json
{
  "user_id": "1",
  "conversation_id": "chat-002",
  "message": "确认",
  "auth_token": "从 Java 登录接口获取的 JWT"
}
```

如果 `chat-002` 没有待确认动作，返回：

```text
当前会话没有待确认的操作。
```

## 第四阶段：真正 AI 回复建议

本阶段新增“基于真实 Java 工单详情生成 AI 回复建议”的能力。

核心链路：

```text
POST /ai/tickets/{ticket_id}/reply-suggestion
↓
TicketAiService
↓
JavaTicketClient.get_ticket_detail()
↓
Java GET /tickets/{id}/detail
↓
ReplySuggestionPromptBuilder 构造 prompt
↓
LLMClient 调用 mock LLM 或真实 LLM
↓
返回结构化 AI 回复建议
```

这个接口只生成建议，不会自动保存到 Java 数据库，也不会写入 `ticket_reply`。AI 生成内容必须先由客服人工检查，后续确认采纳后，才适合交给 Java 保存为 `TicketReplyType.AI`。

新增接口：

```http
POST http://127.0.0.1:8001/ai/tickets/1/reply-suggestion
Content-Type: application/json
```

请求体：

```json
{
  "auth_token": "从 Java 登录接口获取的 JWT"
}
```

响应示例：

```json
{
  "ticket_id": 1,
  "suggestion": "建议先向用户确认问题是否仍然存在，并请用户提供相关错误截图或具体操作步骤，以便进一步定位原因。",
  "confidence": 0.8,
  "reason": "基于工单详情和历史回复生成",
  "next_steps": [
    "请客服人工检查回复建议是否准确",
    "确认后再发送给用户或保存为 AI 回复"
  ],
  "source": "llm"
}
```

Prompt 会使用：

```text
工单标题
工单内容
工单状态
工单优先级
工单分类
历史回复列表
```

Prompt 不会放入 JWT Token，也不会要求 LLM 执行创建、修改、删除、保存等写操作。`password` 字段会被忽略。

本地 mock LLM 模式：

```env
LLM_MOCK_MODE=true
```

这种模式不调用真实模型，适合本地测试接口流程。

真实 LLM 模式：

```env
LLM_MOCK_MODE=false
LLM_API_KEY=你的本地测试 key
LLM_API_BASE_URL=你的 LLM 服务地址
LLM_MODEL=你的模型名
```

不要提交真实 key。

Postman 测试流程：

```text
1. 启动 Java 后端
2. 调用 Java /auth/login 获取 JWT
3. 确认 Java GET /tickets/{id}/detail 可用
4. 启动 Python AI 服务
5. POST /ai/tickets/{ticket_id}/reply-suggestion 并传 auth_token
```

Java 未启动时，返回：

```text
获取工单详情失败：无法连接 Java API
```

未登录时，返回：

```text
获取工单详情失败：Java API 返回未登录，请检查 auth_token
```

无权限时，返回：

```text
获取工单详情失败：当前用户无权限访问该工单
```

工单不存在时，返回：

```text
获取工单详情失败：工单不存在
```

后续 TODO：

```text
在 Java 服务层增加 saveAiReplySuggestion 方法。
客服人工确认采纳后，把 AI 建议保存为 TicketReplyType.AI。
保存后记录 OperationLog，并删除 ticket:detail:{id} Redis 缓存。
```

## 启动服务

```bash
uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

启动后可以访问自动文档：

```text
http://127.0.0.1:8001/docs
```

## 测试接口

测试健康检查：

```bash
curl http://127.0.0.1:8001/health
```

测试聊天接口：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"你好，你能做什么？\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -ContentType "application/json" `
  -Body '{"message":"你好，你能做什么？"}'
```

带 Java JWT Token 调用：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -ContentType "application/json" `
  -Body '{"message":"查一下所有处理中工单","auth_token":"你的JWT_TOKEN"}'
```

## Mock 模式

当 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 任意一项为空时，普通聊天不会调用真实大模型，而是直接返回固定 Mock 回答：

```text
我是智能工单助手，可以帮你查询、创建、修改和总结工单。
```

这个模式适合没有 API Key 时先跑通项目结构、接口和测试。

## 真实 LLM 模式

当 `.env` 中完整配置以下三项时，普通聊天会调用兼容 OpenAI Chat Completions 风格的接口：

```env
LLM_API_KEY=你的 API Key
LLM_BASE_URL=https://example.com/v1
LLM_MODEL=example-model
```

实际请求地址为：

```text
{LLM_BASE_URL}/chat/completions
```

如果模型服务超时、返回非 2xx 状态码，或响应结构不符合预期，接口会返回统一的友好错误：

```json
{
  "success": false,
  "code": "LLM_CALL_FAILED",
  "message": "模型服务调用失败，请稍后重试",
  "detail": null
}
```

## LangChain Tool 基础

Tool 是给 Agent 使用的一个可调用能力单元。它通常包含 name、description、输入参数 schema 和执行逻辑。模型或 Agent 可以根据用户问题选择合适的 Tool。本阶段不做真正的模型自动 Tool Calling，只做“简单规则路由 + Tool 调用”，方便先理解 Tool 的基本结构。

当前实现的 Tool：

```text
get_agent_capabilities
search_tickets
create_ticket
update_ticket_status
```

Tool description:

```text
Get the current capabilities of the ticket agent. Use this tool when the user asks what the agent can do, what functions are available, or how the agent can help. Do not use this tool to query, create, update, or delete tickets.
```

这个 Tool 不需要输入参数，返回当前 Agent 能力说明：

```json
{
  "capabilities": [
    "查询工单：后续阶段支持按状态、优先级、关键词查询工单",
    "创建工单：后续阶段支持根据用户描述创建新工单",
    "修改工单状态：后续阶段支持修改工单处理状态",
    "总结工单：后续阶段支持总结工单列表和处理情况",
    "生成处理建议：后续阶段支持结合知识库为工单生成处理建议"
  ],
  "current_stage": "LangChain Tool 基础阶段",
  "note": "当前阶段只实现能力说明工具，还没有真正连接工单数据。"
}
```

查看当前工具列表：

```bash
curl http://127.0.0.1:8001/agent/tools
```

Windows PowerShell:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8001/agent/tools" -Method Get
```

触发 Tool 调用：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"你能做什么？\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"message":"你能做什么？"}'
```

以下问题会触发 `get_agent_capabilities`：

- `你能做什么`
- `你有什么功能`
- `你有哪些功能`
- `你可以帮我做什么`
- `有哪些工具`
- `agent 能力`
- `功能列表`

`get_agent_capabilities` 用于能力说明类问题。`search_tickets` 用于工单查询类问题，`create_ticket` 用于确认后调用 Java 创建工单，`update_ticket_status` 用于确认后调用 Java 修改工单状态。

## Java API search_tickets 查询工单

本阶段的学习目标是把自然语言查询转换成结构化参数，再调用 Java 后端 `GET /tickets`，并把 Java 返回结果组织成自然语言回答。Python 只负责意图识别和工具编排，不直接查询 MySQL。

`search_tickets` 是一个只读查询工具，Tool name 为：

```text
search_tickets
```

Tool description:

```text
Search real Java backend tickets by status, priority, or keyword. Use this tool when the user wants to find, list, filter, or search tickets. This tool only reads ticket data through the Java API and does not create, update, or delete tickets.
```

输入参数全部可选：

```json
{
  "status": "OPEN",
  "priority": "HIGH",
  "category": "ACCOUNT",
  "keyword": "登录",
  "auth_token": "可选 JWT Token"
}
```

`status` 支持：

```text
OPEN
PROCESSING
DONE
CLOSED
```

`priority` 支持：

```text
LOW
MEDIUM
HIGH
URGENT
```

中文状态映射规则：

```text
未处理 / 待处理 / 打开 / open -> OPEN
处理中 / 处理 / processing -> PROCESSING
已完成 / 完成 / done -> DONE
已关闭 / 关闭 / closed -> CLOSED
```

中文优先级映射规则：

```text
低优先级 / 低 / low -> LOW
中优先级 / 中 / 普通 / medium -> MEDIUM
高优先级 / 高 / high -> HIGH
紧急 / 非常紧急 / urgent -> URGENT
```

通过 `/agent/chat` 触发查询：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"查一下所有工单\"}"
```

测试高优先级未处理工单：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"帮我查一下高优先级未处理工单\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"message":"帮我查一下高优先级未处理工单"}'
```

查看工具列表：

```bash
curl http://127.0.0.1:8001/agent/tools
```

Windows PowerShell:

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8001/agent/tools" -Method Get
```

运行测试：

```bash
pytest
```

当前 `/agent/chat` 的 Tool 路由逻辑：

```text
用户 message
-> AgentToolService.chat_with_tools(message, user_id, conversation_id)
-> 根据 user_id + conversation_id 生成 pending_intent key
-> 如果是确认/取消输入，优先走 Java pending_action 确认/取消逻辑
-> 如果当前会话有 pending_intent，优先把本轮 message 当作补充字段合并
-> 如果是能力询问，调用 get_agent_capabilities
-> 如果是工单查询，抽取 status / priority / keyword，调用 search_tickets
-> 如果是创建工单，抽取 title / description / priority
   -> 如果字段缺失，保存 pending_intent 并返回 MISSING_FIELDS
   -> 如果字段完整，创建 Java pending_action，等待确认
-> 如果是修改工单状态，抽取 ticket_id / status
   -> 如果字段缺失，保存 pending_intent 并返回 MISSING_FIELDS
   -> 如果字段完整，创建 Java pending_action，等待确认
-> 回复建议、摘要、优先级建议、分类建议、相似工单、SLA 风险如果缺 ticket_id，也会保存 pending_intent 继续追问
-> 否则返回 UNKNOWN_INTENT
```

## Java API create_ticket 创建工单

本阶段的学习目标是把自然语言创建请求转换成 JSON 参数，再进入人工确认流程。用户确认后，`create_ticket` 调用 Java 后端 `POST /tickets` 创建真实工单。

`create_ticket` 是一个确认后调用 Java API 创建工单的工具，Tool name 为：

```text
create_ticket
```

Tool description:

```text
Create a new ticket through the Java backend with title, description, and priority. Use this tool only when the user clearly wants to create or submit a new ticket and provides all required fields: title, description, and priority. Do not use this tool if any required field is missing. This tool changes Java backend data and requires user confirmation before execution.
```

输入参数：

```json
{
  "title": "登录失败",
  "description": "用户输入正确密码后仍提示错误",
  "priority": "HIGH"
}
```

`title`、`description`、`priority` 都是必填字段。创建工单是写操作，缺少任何字段时都不能替用户编造，也不能默认优先级。通过 `/agent/chat` 触发创建时，系统会先保存 `pending_action` 并要求用户回复“确认”后才真正调用 `create_ticket`。

新工单的默认状态、ID 和用户归属由 Java 后端决定。Python 不生成工单 ID，也不写入本地 tickets。

通过 `/agent/chat` 完整创建：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"帮我创建一个工单，标题是登录失败，描述是用户输入正确密码后仍提示错误，优先级高\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"message":"帮我创建一个工单，标题是登录失败，描述是用户输入正确密码后仍提示错误，优先级高"}'
```

字段缺失时会追问，不会创建工单：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"帮我创建一个登录问题工单\"}"
```

创建成功后，可以继续用 `search_tickets` 通过 Java API 查到新工单：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"查询包含登录的工单\"}"
```

运行测试：

```bash
pytest
```

## Java API update_ticket_status 修改工单状态

本阶段的学习目标是让 Agent 执行受控业务动作：从自然语言中提取工单 ID 和目标状态，保存 `pending_action` 等待用户确认。用户确认后，`update_ticket_status` 调用 Java 后端 `PUT /tickets/{id}/status`，状态合法性由 Java 业务规则校验。

`update_ticket_status` 是一个确认后调用 Java API 修改工单状态的工具，Tool name 为：

```text
update_ticket_status
```

Tool description:

```text
Update the status of an existing Java backend ticket. Use this tool only when the user wants to change a ticket status and provides a ticket id and target status. This tool changes Java backend data, relies on Java business validation, and requires user confirmation before execution.
```

输入参数：

```json
{
  "ticket_id": 3,
  "status": "PROCESSING"
}
```

`ticket_id` 从 `3 号工单`、`id 为 3 的工单` 这类表达中提取。`status` 复用状态映射：

```text
未处理 / 待处理 / 打开 / open -> OPEN
处理中 / 处理 / processing -> PROCESSING
已完成 / 完成 / done -> DONE
已关闭 / 关闭 / closed -> CLOSED
```

状态流转由 Java 后端决定。当前 Java 后端主要规则为：

```text
OPEN -> PROCESSING
OPEN -> CLOSED
PROCESSING -> CLOSED
CLOSED 不允许继续修改
```

如果目标状态或状态流转不合法，Java 会返回统一错误，Python 会把错误说明整理成友好的自然语言回答。

工单不存在时返回：

```text
Java API 返回工单不存在
```

目标状态无法识别时返回：

```text
目标状态不合法。当前支持的状态包括：OPEN、PROCESSING、DONE、CLOSED；常用中文包括：未处理、处理中、已完成、已关闭。
```

非法流转时返回：

```text
工单状态流转不合法
```

通过 `/agent/chat` 修改为处理中：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"把 1 号工单改成处理中\"}"
```

修改为已完成：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"将 2 号工单状态改为已完成\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"message":"把 1 号工单改成处理中"}'
```

非法流转测试：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"把 5 号工单改成未处理\"}"
```

修改成功后，可以继续用 `search_tickets` 查到新状态：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"message\":\"查一下处理中工单\"}"
```

运行测试：

```bash
pytest
```

## Human-in-the-loop 人工确认

本阶段学习真实 Agent 项目中的安全机制：

```text
查询可以直接执行，写操作必须确认。
```

`search_tickets` 是只读 Tool，不改变 Java 数据，所以可以直接执行。`create_ticket` 和 `update_ticket_status` 会改变 Java 后端数据，因此通过 `/agent/chat` 触发时必须先进入人工确认流程。

当前确认流程：

```text
用户提出创建或修改请求
-> Agent 根据 user_id + conversation_id 定位 pending_intent
-> Agent 抽取参数并校验
-> 如果字段缺失，保存 Python pending_intent 并追问
-> 用户补充字段后合并 pending_intent
-> 字段完整后清理 pending_intent
-> 创建 Java pending_action
-> 返回 PENDING_CONFIRMATION
-> 用户回复“确认”
-> Python 使用当前请求 auth_token 调 Java confirm
-> Java 校验当前用户和会话
-> Java 执行 pending_action 对应业务动作
-> Java 将 pending_action 标记为 CONFIRMED
```

取消流程：

```text
用户提出创建或修改请求
-> Agent 可能保存了 Python pending_intent 或 Java pending_action
-> 用户回复“取消”
-> Python 清理当前 user_id + conversation_id 下的 pending_intent
-> Python 调 Java cancel 当前会话的 pending_action
-> 不执行创建、修改状态、保存回复建议
```

Java `pending_action` 保存待确认的写操作，payload 结构类似：

```json
{
  "actionType": "UPDATE_TICKET_STATUS",
  "payload": {
    "ticketId": 3,
    "status": "PROCESSING"
  }
}
```

`create_ticket` 的 Java pending action 保存 `title`、`content`、`priority`；`update_ticket_status` 的 Java pending action 保存 `ticketId` 和目标 `status`。pending action 不保存 token，确认时必须重新携带当前有效 `auth_token`。

创建工单确认流程，第一步发起创建请求：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"1\",\"conversation_id\":\"chat-001\",\"message\":\"帮我创建一个工单，标题是登录失败，描述是用户输入正确密码后仍提示错误，优先级高\"}"
```

第二步确认执行：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"1\",\"conversation_id\":\"chat-001\",\"message\":\"确认\"}"
```

取消执行：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"1\",\"conversation_id\":\"chat-001\",\"message\":\"取消\"}"
```

修改工单状态确认流程，第一步发起修改请求：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"1\",\"conversation_id\":\"chat-002\",\"message\":\"把 1 号工单改成处理中\"}"
```

第二步确认执行：

```bash
curl -X POST http://127.0.0.1:8001/agent/chat \
  -H "Content-Type: application/json" \
  -d "{\"user_id\":\"1\",\"conversation_id\":\"chat-002\",\"message\":\"确认\"}"
```

Windows PowerShell:

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8001/agent/chat" `
  -Method Post `
  -ContentType "application/json" `
  -Body '{"user_id":"1","conversation_id":"chat-002","message":"确认"}'
```

没有 pending action 时输入“确认”会返回：

```text
当前会话没有待确认的操作。
```

没有 pending action 时输入“取消”会返回：

```text
当前会话没有待取消的操作。
```

运行测试：

```bash
pytest
```

## 错误处理

项目使用 `AppException` 表示业务错误，并在 `app/main.py` 注册全局异常处理器。统一错误响应格式为：

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "错误说明",
  "detail": null
}
```

未处理异常会返回 `INTERNAL_ERROR`，不会把 Python 堆栈暴露给前端。Tool 调用失败会返回：

```json
{
  "success": false,
  "code": "TOOL_CALL_FAILED",
  "message": "工具调用失败，请稍后重试",
  "detail": null
}
```

`search_tickets`、`create_ticket`、`update_ticket_status` 调用 Java 失败时会返回友好说明。例如 Java 未启动：

```text
无法连接 Java API，请确认 Java 后端是否已启动：http://127.0.0.1:8080
```

Java 返回未登录时：

```text
Java API 返回未登录，请检查 auth_token 或 JAVA_API_TOKEN
```

Java 返回无权限时：

```text
Java API 返回无权限，当前用户不能执行该操作
```

Java 返回工单不存在时：

```text
Java API 返回工单不存在
```

创建工单缺少必填字段时，`/agent/chat` 会直接追问，不会调用 `create_ticket`，也不会创建不完整工单。

修改状态缺少工单 ID 或目标状态时，`/agent/chat` 会直接追问，不会调用 `update_ticket_status`。工单不存在、目标状态非法或状态流转不合法时，会返回 Java 后端给出的友好说明，不会暴露内部堆栈。

pending action 相关提示：

```text
当前会话没有待确认的操作。
当前会话没有待取消的操作。
待确认操作执行失败，请稍后重试。
待确认操作类型不支持，已取消本次操作。
```

## 日志

日志使用 Python 标准库 `logging`，格式包含时间、日志级别、模块名和消息。日志级别由 `.env` 中的 `LOG_LEVEL` 控制。启动服务后，日志会输出到控制台。

项目不会记录 API Key。`POST /agent/chat` 收到请求时会记录 info 日志；触发 Tool 时会记录：

```text
Agent tool called: get_agent_capabilities
```

调用 `search_tickets` 时会记录：

```text
Agent tool called: search_tickets
Search tickets params: {...}
Search tickets result total: N
```

判断为创建工单意图时会记录：

```text
Agent intent detected: create_ticket
```

调用 `create_ticket` 时会记录：

```text
Agent tool called: create_ticket
Create ticket params: {...}
Created Java ticket id: N
```

字段缺失时会记录：

```text
Create ticket missing fields: [...]
```

判断为修改工单状态意图时会记录：

```text
Agent intent detected: update_ticket_status
```

调用 `update_ticket_status` 时会记录：

```text
Agent tool called: update_ticket_status
Update ticket status params: {...}
Updated Java ticket id: N from OLD_STATUS to NEW_STATUS
```

字段缺失、非法流转、工单不存在时会记录：

```text
Update ticket status missing fields: [...]
Invalid ticket status transition: OLD_STATUS -> NEW_STATUS
Ticket not found: ID
```

保存、确认、取消 pending action 时会记录：

```text
Create ticket missing fields: [...]
Update ticket status missing fields: [...]
Java pending action created: create_ticket conversation_id=chat-001
Java pending action created: update_ticket_status conversation_id=chat-001
Java pending action confirm failed: conversation_id=chat-001 status_code=400
Java pending action cancel failed: conversation_id=chat-001 status_code=400
```

没有触发 Tool、走普通 LLMService 时会记录：

```text
Agent chat fallback to LLMService
```

真实 LLM 调用失败或 Tool 调用失败时会记录 exception 日志。

## 运行测试

```bash
pytest
```

当前测试覆盖：

- `/health` 返回 200。
- `/health` 返回 `status=ok`。
- `/agent/chat` 正常 message 返回 200。
- `/agent/chat` 返回 `answer`。
- `/agent/chat` 空 message 返回参数校验错误。
- `get_all_tools()` 返回工具列表。
- 工具列表包含 `get_agent_capabilities`。
- `/agent/tools` 返回 200 和工具名。
- `/agent/chat` 能根据能力类问题触发 Tool。
- `get_all_tools()` 包含 `search_tickets`。
- `/agent/tools` 返回 `search_tickets`。
- `search_tickets` 无参数时调用 Java API 返回工单列表。
- `search_tickets` 支持按 `status`、`priority`、`keyword` 查询。
- `/agent/chat` 能根据工单查询类问题触发 `search_tickets`。
- `/agent/chat` 对无查询结果给出友好说明。
- `get_all_tools()` 包含 `create_ticket`。
- `/agent/tools` 返回 `create_ticket`。
- `create_ticket` 信息完整时确认后调用 Java API 创建工单。
- `create_ticket` 创建的新工单默认状态、ID 和用户归属由 Java 后端决定。
- 创建后 `search_tickets` 能通过 Java API 查到新工单。
- `/agent/chat` 能根据完整创建请求触发 `create_ticket`。
- `/agent/chat` 在缺少标题、描述或优先级时会追问。
- 中文 `高优先级` 能映射为 `HIGH`，中文 `紧急` 能映射为 `URGENT`。
- `get_all_tools()` 包含 `update_ticket_status`。
- `/agent/tools` 返回 `update_ticket_status`。
- `update_ticket_status` 确认后调用 Java API，状态流转以 Java 后端规则为准。
- `update_ticket_status` 会透传 Java 返回的工单不存在、非法状态和非法流转错误。
- `/agent/chat` 能根据状态修改请求触发 `update_ticket_status`。
- `/agent/chat` 在缺少工单 ID 或目标状态时会追问。
- 修改后 `search_tickets` 能查到新状态。
- 查询工单不需要确认。
- 创建工单会先保存 pending action，确认后才执行。
- 修改工单状态会先保存 pending action，确认后才执行。
- 用户取消后不会执行写操作，pending action 会清空。
- 没有 pending action 时输入确认或取消不会误执行 Tool。
- 普通消息仍然走 LLMService/Mock 流程。
- Tool 调用失败时返回友好错误。

## 下一阶段计划

下一阶段可以把当前开发用内存 `pending_intent` 升级为 Redis 或 Java 表，解决服务重启和多 worker 场景下的状态共享问题；也可以继续增强字段提取能力，让用户用更自然的多轮表达补齐标题、描述、优先级和工单 ID。

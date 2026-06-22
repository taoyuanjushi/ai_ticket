# ticket-agent-python

`ticket-agent-python` 是一个用于学习 AI Agent 应用开发的 Python 项目。当前阶段在已有 FastAPI Agent 服务基础上，引入 LangChain Tool 的最小实践：实现工具定义、工具注册、工具列表接口，以及 `/agent/chat` 中的简单规则路由。

第二阶段已把工单工具的数据源从 Python 本地 mock 数据切换为 Java Spring Boot REST API。Python 不直接连接 MySQL，不维护真实工单数据；写操作继续使用简化版内存 Human-in-the-loop 人工确认机制。

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

为了避免多个用户或多个会话共用同一个 `pending_action`，`/agent/chat` 请求体新增 `user_id` 和 `conversation_id`。写操作产生的 `pending_action` 会按 `session_key` 保存在内存字典中，确认或取消时也只处理当前 `session_key` 对应的动作。

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

`session_key` 生成规则：

```text
优先使用 conversation_id -> conversation:{conversation_id}
没有 conversation_id 时使用 user_id -> user:{user_id}
两个都没有时使用 default
```

`default` 只适合本地学习和测试。正式接入前端或 Java 后端时，必须传 `user_id` 或 `conversation_id`，否则多个请求仍可能落到同一个默认会话。

当前 `PendingAction` 结构：

```json
{
  "tool_name": "update_ticket_status",
  "args": {
    "ticket_id": 3,
    "status": "PROCESSING"
  },
  "summary": "将 3 号工单状态修改为 PROCESSING",
  "action_type": "write",
  "created_at": 1710000000.0
}
```

`PendingActionStore` 当前使用 Python 内存字典保存状态：

```text
session_key -> PendingAction
```

默认过期时间为 5 分钟。确认或取消后，会删除当前 `session_key` 下的 `pending_action`，不会影响其他用户或其他会话。

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
-> 根据 conversation_id 或 user_id 生成 session_key
-> 如果是确认/取消输入，优先处理当前 session_key 下的 pending_action
-> 如果是能力询问，调用 get_agent_capabilities
-> 如果是工单查询，抽取 status / priority / keyword，调用 search_tickets
-> 如果是创建工单，抽取 title / description / priority
   -> 如果字段缺失，返回追问
   -> 如果字段完整，保存当前 session_key 下的 pending_action，等待确认
-> 如果是修改工单状态，抽取 ticket_id / status
   -> 如果字段缺失，返回追问
   -> 如果字段完整且预校验通过，保存当前 session_key 下的 pending_action，等待确认
-> 否则走 LLMService.chat(message)
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
-> Agent 根据 conversation_id 或 user_id 生成 session_key
-> Agent 抽取参数并校验
-> 保存当前 session_key 下的 pending_action
-> 返回确认提示
-> 用户回复“确认”
-> 只读取当前 session_key 下的 pending_action
-> 执行 pending_action 对应 Tool
-> 清空当前 session_key 下的 pending_action
```

取消流程：

```text
用户提出创建或修改请求
-> Agent 保存当前 session_key 下的 pending_action
-> 用户回复“取消”
-> 不执行 Tool
-> 清空当前 session_key 下的 pending_action
```

`pending_action` 保存待确认的写操作，结构类似：

```json
{
  "tool_name": "update_ticket_status",
  "args": {
    "ticket_id": 3,
    "status": "DONE"
  },
  "summary": "将 3 号工单状态修改为 DONE",
  "action_type": "write",
  "created_at": 1710000000.0
}
```

`create_ticket` 的 pending action 会保存 `title`、`description`、`priority`；`update_ticket_status` 的 pending action 会保存 `ticket_id` 和目标 `status`。如果请求中传入了 `auth_token`，当前实现也会把该 token 暂存在内存 pending action 中，便于确认请求没有再次传 token 时继续调用 Java。用户确认后，系统根据 `tool_name` 调用对应 Tool；用户取消后，系统只清空 pending action，不执行 Tool。

当前使用 `AgentStateService` + `PendingActionStore` 把 pending action 按 `session_key` 保存在内存中。这个实现适合学习流程，但服务重启后会丢失状态，多 worker 部署时也不能共享状态。生产化时不要长期把敏感 token 保存在内存状态中，后续可以升级为 LangGraph State + interrupt，或者把待确认动作和授权上下文交给 Java 后端管理。

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
Pending action created: create_ticket session_key=user:1
Pending action created: update_ticket_status session_key=conversation:chat-001
Pending action approved: create_ticket session_key=user:1
Pending action approved: update_ticket_status session_key=conversation:chat-001
Pending action cancelled: create_ticket session_key=user:1
Pending action cancelled: update_ticket_status session_key=conversation:chat-001
Pending action executed and cleared session_key=conversation:chat-001
No pending action to approve for session_key=conversation:chat-001
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

下一阶段可以把当前内存 pending action 升级为 LangGraph State + interrupt，或者把待确认操作交给 Java 后端保存，解决服务重启和多 worker 场景下的状态共享问题。

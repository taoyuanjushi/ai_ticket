# 重复造轮子与旧代码审计报告

生成时间：2026-06-29  
范围：`frontend`、`java/hello-demo`、`ticket-agent-python`、`docker-compose.prod.yml`  
方式：只读扫描依赖、配置、入口、调用关系和明显未引用文件；本报告生成前未对业务代码做清理。

## 一、项目已有能力

### 前端

- 技术栈：React 18、Vite 6、TypeScript、React Router、TanStack Query、Zustand、lucide-react、Vitest、ESLint、Tailwind。
- 已有复用能力：
  - `frontend/src/api/http.ts` 统一处理 Java API 调用、JWT、认证失效。
  - `frontend/src/api/client.ts` 统一封装业务接口。
  - `frontend/src/state/authStore.ts` 统一保存登录状态。
  - `frontend/src/utils/aiMessages.ts` 统一处理 AI 结构化消息和敏感字段过滤。
  - `frontend/src/components/ai/*` 已替代旧版 AI JSON 展示组件。
- 配置确认：
  - `frontend/vite.config.ts` 只代理 `/api` 到 Java `http://127.0.0.1:8080`。
  - 未发现前端直接调用 Python `8001`、`/agent/chat` 或 `/ai/tickets`。

### Java 后端

- 技术栈：Java 21、Spring Boot 4、MyBatis-Plus、MySQL、Redis、Jackson、JWT、BCrypt。
- 已有复用能力：
  - `Result` / `PageResult` 统一响应格式。
  - `JwtInterceptor`、`CurrentUser`、`PermissionService` 统一认证和权限判断。
  - `TicketService` / `TicketReplyService` / `AiPendingActionService` 承担业务逻辑。
  - `OperationLogService` 已做 token/password 等敏感字段脱敏。
  - `AiPendingActionService` 已集中处理 AI 写操作的 pending_action、确认、取消、审计。
- 配置确认：
  - Docker 中 Java 通过 `AI_SERVICE_BASE_URL=http://python-ai:8001` 调 Python。
  - Java 本地默认 `ai.service.base-url=http://127.0.0.1:8001`，属于开发默认值。
  - MyBatis 日志默认 `NoLoggingImpl`，避免 SQL 日志噪声和敏感信息外泄风险。

### Python AI Agent

- 技术栈：FastAPI、Pydantic、pydantic-settings、httpx、pytest、langchain、langchain-core。
- 已有复用能力：
  - `AgentToolService.handle()` 已返回结构化 `AgentResponse`。
  - `ResponseBuilder` 已统一构造 AI 响应类型。
  - `JavaTicketClient` 统一访问 Java API，并可透传 Java JWT。
  - `TicketAiService`、`ai_capabilities/*` 负责基于工单上下文生成建议。
  - `guardrail_service.py`、`grounding.py` 有敏感字段过滤和 grounding 保护。
- 配置确认：
  - Docker 中 Python 通过 `JAVA_API_BASE_URL=http://java-backend:8080` 回调 Java。
  - `.env.example` 中的 `127.0.0.1:8080` 属于本地开发默认值。

## 二、审计结论

整体结论：主链路已经比较收敛，前端没有直连 Python，Java 已把旧 AI 回复直接保存接口挡住并要求走 pending_action。真正适合当前阶段处理的是低风险 P1：删除未引用前端组件、删除 Python 旧文本解析器、去掉未使用的完整 `langchain` 依赖。涉及测试兼容、旧 demo 或外部调用契约的内容只列为需确认，不直接删除。

## 三、问题清单

| 优先级 | 类型 | 位置 | 发现 | 风险 | 建议 | 是否需要人工确认 |
| --- | --- | --- | --- | --- | --- | --- |
| P0 | 旧入口确认 | `java/hello-demo/src/main/java/com/example/hello_demo/controller/TicketController.java` | `POST /tickets/{id}/ai-replies` 仍存在，但当前实现直接抛出“需要先创建 pending_action 并确认”。 | 外部客户端如果仍调用旧接口，会收到 400；但不会绕过 pending_action。 | 保留拦截行为。是否彻底删除接口要等确认是否有外部调用方。 | 是 |
| P0 | 旧写工具确认 | `ticket-agent-python/app/tools/ticket_tools.py`、`ticket-agent-python/app/services/agent_tool_service.py` | `create_ticket`、`update_ticket_status` 仍可作为 LangChain tool 被 `.invoke()`，测试也直接调用。主 `/agent/chat` 流程已创建 Java pending_action。 | 如果未来暴露通用 tool 执行入口，可能绕过确认；当前未发现 HTTP 直接执行工具入口。 | 暂不删除。后续要么改成只读/测试工具，要么让直接写工具也只创建 pending_action。 | 是 |
| P1 | delete | `frontend/src/components/AiResponseView.tsx` | 旧 AI 展示组件无任何 import；现在由 `AiMessageBubble`、`AiJsonResultCard`、`PendingActionCard` 承担展示。 | 死代码会误导后续维护者，以为存在两套 AI 渲染链路。 | 删除文件。 | 否 |
| P1 | delete | `ticket-agent-python/app/api/agent_api.py` | `build_agent_chat_response(answer: str)` 以及它依赖的文本分类/JSON 解析 helper 已不再被调用；当前路由只用 `build_agent_chat_response_from_agent_response()`。 | 保留旧解析器会造成两套响应判断规则，后续修改容易改错位置。 | 删除旧解析器和未用 import。 | 否 |
| P1 | delete | `ticket-agent-python/requirements.txt` | 项目只从 `langchain_core.tools` 导入 `tool` / `BaseTool`，未使用 `langchain` planner、agent、chain。 | 多装一个大依赖，增加镜像体积和依赖冲突面。 | 删除 `langchain==0.3.30`，保留实际使用的 `langchain-core`。 | 否 |
| P1 | shrink | `ticket-agent-python/app/services/agent_tool_service.py` | `AgentToolService.handle()` 调 `chat_with_tools()` 得到 JSON 字符串，再 `_to_agent_response()` 解析回 `AgentResponse`。 | 同一层内序列化再反序列化，规则分散。 | 后续把 dispatch/format 方法改为直接返回 `AgentResponse`；本轮可先不动，避免一次改动过大。 | 否 |
| P1 | delete | `ticket-agent-python/app/services/agent_tool_service.py`、`ticket-agent-python/app/services/llm_service.py` | `self.llm_service` 被赋值但没有实际读取；测试还在 monkeypatch `agent_api.llm_service.settings`。 | 旧依赖会让人误以为 AgentToolService 仍直接调用 LLMService。 | 与测试一起清理；本轮不先动，避免测试夹具大面积调整。 | 否 |
| P1 | delete | `ticket-agent-python/app/services/agent_state_service.py`、`ticket-agent-python/app/services/pending_action_store.py` | 注释已说明 Java pending_action 是真实确认状态；Python in-memory store 只为老 demo/test 保留。 | 两套 pending_action 概念会误导维护者。 | 需要先改测试和 README，再删除。当前标记为“需确认”。 | 是 |
| P2 | native | `java/hello-demo/src/main/java/com/example/hello_demo/common/Result.java`、`PageResult.java`、`vo/*` | Java 21 可用 record 减少 VO/getter/setter 样板。 | 主要是样板代码，不影响主链路。改 record 可能影响 Jackson、MyBatis 或已有测试断言。 | 暂不动；以后专门做 Java DTO/VO 收缩。 | 否 |
| P2 | shrink | `java/hello-demo/src/main/java/com/example/hello_demo/security/JwtInterceptor.java` | 手写 JSON 响应字符串和 escape。项目已有 Jackson。 | 当前逻辑小且稳定，收益不高。 | 后续可注入 `ObjectMapper`，但本轮不为样式改动增加构造复杂度。 | 否 |
| P2 | shrink | `java/hello-demo/src/main/java/com/example/hello_demo/client/AiClient.java`、`AiPendingActionService.java` | 多处直接 new/use ObjectMapper，项目已有 Jackson 能力。 | 可维护性小问题。 | 后续统一 ObjectMapper Bean；本轮不动。 | 否 |
| P2 | 保留 | `frontend/src/api/mock.ts`、`ticket-agent-python/app/tools/mock_ticket_data.py` | 存在 mock/fake 数据。 | 看起来像旧代码，但测试和本地演示仍使用。 | 保留，除非项目决定移除 mock 模式。 | 是 |

## 四、P0 状态

- 前端直连 Python：未发现。
- 前端硬编码 Python `8001`：未发现。
- Java 旧 AI 回复直接保存：接口仍存在，但已被拦截，不能直接保存。
- pending_action 中保存 token：Java `AiPendingActionService` 已校验并拒绝 token/authorization。
- 操作日志 token/password：Java `OperationLogService` 已脱敏；Python 工具参数日志中也有 `auth_token` 脱敏路径。
- Python 绕过 Java 权限：主链路通过 Java JWT 调 Java API；直接 tool `.invoke()` 仍需人工确认是否只用于测试/内部。

## 五、本轮建议执行

本轮只执行低风险 P1：

1. 删除未引用的 `frontend/src/components/AiResponseView.tsx`。
2. 删除 `ticket-agent-python/app/api/agent_api.py` 中未使用的旧文本响应解析器。
3. 删除 `ticket-agent-python/requirements.txt` 中未使用的 `langchain==0.3.30`，保留 `langchain-core`。

暂不执行：

1. 删除 Python in-memory pending_action：测试仍依赖，需要单独迁移。
2. 重写 AgentToolService 返回类型：收益明确，但改动面大于本轮低风险清理。
3. Java record 化：属于样板瘦身，不是当前安全/旧链路问题。
4. 删除旧 AI 回复接口：当前已安全拦截，是否删除需要确认外部契约。

## 六、验证计划

- 前端：`npm.cmd run lint`、`npm.cmd run build`。
- Python：`python -m pytest`。
- Java：`.\mvnw.cmd test` 或至少 `.\mvnw.cmd -DskipTests compile`。
- Docker：本轮不改 Docker 编排，若需要完整验收再跑 `docker-compose -f docker-compose.prod.yml up --build`。

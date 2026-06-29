# 重复造轮子与旧代码清理报告

生成时间：2026-06-29  
前置报告：`WHEEL_REINVENTION_AUDIT_REPORT.md`  
清理原则：只执行证据明确、低风险、可由现有测试覆盖的 P1 项；P0 中需要外部契约确认的旧入口只记录，不强删。

## 一、本轮已清理

### 1. 删除旧前端 AI 渲染组件

- 删除文件：`frontend/src/components/AiResponseView.tsx`
- 原因：
  - 全仓库无 import。
  - 当前 AI 展示已由 `frontend/src/components/ai/*` 承担。
  - 删除后避免维护者误以为存在两套 AI 回复渲染链路。

### 2. 删除 Python API 层旧文本解析器

- 修改文件：`ticket-agent-python/app/api/agent_api.py`
- 删除内容：
  - `build_agent_chat_response(answer: str)`
  - `classify_text_answer`
  - `parse_json_object`
  - `is_known_ai_json`
  - `read_string`
  - `to_string_list`
  - `contains_any`
  - `parse_create_missing_fields`
  - 对应未使用 import：`json`、`Any`
- 保留内容：
  - `build_agent_chat_response_from_agent_response(response: AgentResponse)`
- 原因：
  - `/agent/chat` 当前调用 `AgentToolService.handle()`，已经得到结构化 `AgentResponse`。
  - API 层只需要把 `AgentResponse` 映射为 `AgentChatResponse`，不需要再猜测文本含义。

### 3. 移除未使用的完整 LangChain 依赖

- 修改文件：`ticket-agent-python/requirements.txt`
- 删除：
  - `langchain==0.3.30`
- 保留：
  - `langchain-core>=0.3.85,<1.0.0`
- 原因：
  - 当前代码只使用 `langchain_core.tools.tool` 和 `BaseTool`。
  - 未使用 LangChain planner、agent、chain。

## 二、未清理但已记录

### 1. Java 旧 AI 回复保存接口

- 位置：`java/hello-demo/src/main/java/com/example/hello_demo/controller/TicketController.java`
- 当前状态：`POST /tickets/{id}/ai-replies` 仍存在，但直接返回业务错误，要求走 pending_action。
- 本轮处理：不删除。
- 原因：这属于外部 API 契约问题，需要确认是否有旧客户端依赖该路径。

### 2. Python 直接写工具

- 位置：`ticket-agent-python/app/tools/ticket_tools.py`
- 当前状态：`create_ticket`、`update_ticket_status` 仍可在测试中直接 `.invoke()`。
- 本轮处理：不删除。
- 原因：测试覆盖大量直接 tool 调用；要删除需要先决定这些工具是“内部测试工具”还是“生产可执行工具”。

### 3. Python in-memory pending_action 兼容层

- 位置：
  - `ticket-agent-python/app/services/agent_state_service.py`
  - `ticket-agent-python/app/services/pending_action_store.py`
- 当前状态：注释已说明真实写确认由 Java pending_action 管理，但测试仍依赖该兼容层。
- 本轮处理：不删除。
- 原因：需要同步改测试夹具和说明文档。

### 4. Java record 化与 ObjectMapper 收敛

- 当前状态：属于样板代码和小型重复。
- 本轮处理：不做。
- 原因：收益小，容易扩大改动面，不属于当前“旧链路/死代码”清理。

## 三、变更规模

- 删除代码：约 360 行
- 删除依赖：1 个完整 Python 依赖包
- 新增文档：2 个
  - `WHEEL_REINVENTION_AUDIT_REPORT.md`
  - `WHEEL_REINVENTION_CLEANUP_REPORT.md`

## 四、验证结果

### 前端

- `npm.cmd run lint`
  - 结果：通过
  - 备注：存在既有 warning：`I18nProvider.tsx` 同时导出非组件，影响 React Fast Refresh 提示，不是本轮新增错误。
- `npm.cmd run build`
  - 结果：通过
- `npm.cmd run test`
  - 结果：通过，6 个测试文件，42 个测试。
  - 备注：存在既有 React Router future flag 与 `act(...)` warning，测试本身通过。

### Python

- `python -m pytest`
  - 结果：失败
  - 原因：全局 Anaconda Python 缺少 `httpx`，测试未进入业务断言。
- `python -m compileall app`
  - 结果：通过
- `.venv\Scripts\python.exe -m pytest`
  - 结果：通过，244 个测试。
  - 备注：存在 `StarletteDeprecationWarning`，不影响本轮变更。

### Java

- `.\mvnw.cmd -DskipTests compile`
  - 结果：通过
- `.\mvnw.cmd test`
  - 结果：通过，93 个测试。
  - 备注：存在 Mockito 动态 agent 的 JDK 未来兼容 warning，不影响本轮变更。

## 五、提交建议

建议本次提交包含：

- `WHEEL_REINVENTION_AUDIT_REPORT.md`
- `WHEEL_REINVENTION_CLEANUP_REPORT.md`
- `frontend/src/components/AiResponseView.tsx` 删除
- `ticket-agent-python/app/api/agent_api.py`
- `ticket-agent-python/requirements.txt`

不建议把以下既有未跟踪文档混在同一个清理提交中，除非它们就是本次要补交的项目交付文档：

- `BUGFIX_REPORT.md`
- `CLEANUP_REPORT.md`
- `LEARNING_GUIDE.md`
- `NEXT_STAGE_PLAN.md`
- `PROJECT_AUDIT_REPORT.md`
- `TASK1_BEGINNER_QA.md`
- `TASK1_IMPLEMENTATION_REPORT.md`
- `TASK1_PROJECT_READING_REPORT.md`

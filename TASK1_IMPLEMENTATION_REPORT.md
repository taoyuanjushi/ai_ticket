# 任务 1 实现报告：工单详情与回复闭环

## 1. 本次目标

在不重写页面、不新增详情页、不改数据库结构、不引入大依赖的前提下，补齐现有工单详情页的回复闭环：

- 复用现有 `TicketDetailPage`。
- 复用 `GET /tickets/{id}/detail`、`POST /tickets/{id}/replies`、`POST /tickets/{id}/ai-replies/pending`。
- 展示回复作者、角色、回复类型、内容和时间。
- STAFF/ADMIN 可以生成 AI 回复建议、编辑建议，并通过 pending action 保存。
- USER 不看到未采纳 AI 建议。
- Python 回复建议稳定输出 `suggestion`、`confidence`、`reason`、`risk_flags`、`intent`、`actionType`。

## 2. 变更文件

### 前端

- `frontend/src/pages/TicketDetailPage.tsx`
  - 回复列表展示 `authorName`、`authorRole`、`replyType`、`content`、`createdAt`。
  - 仅 STAFF/ADMIN 渲染 `TicketAiAssistantPanel`，避免普通 USER 在详情页看到未采纳 AI 建议。

- `frontend/src/components/ai/AiJsonResultCard.tsx`
  - 回复建议卡片改为 STAFF/ADMIN 可编辑。
  - 保存时使用编辑后的 `suggestion`。
  - 取消按钮仅清空当前草稿，不触发保存。
  - 继续展示 `confidence`、`reason`、`risk_flags`。

- `frontend/src/components/ai/TicketAiAssistantPanel.tsx`
  - 保存 AI 回复建议时调用 `api.createAiReplyPending(...)`，不直接保存回复。
  - 将 `confidence`、`reason`、`risk_flags` 一并传给 Java pending action。
  - confirm 后刷新 `ticket-detail` 和 `tickets` 查询，确保回复列表更新。

- `frontend/src/api/client.ts`
  - `createAiReplyPending` 支持可选 meta：`confidence`、`reason`、`riskFlags`。

- `frontend/src/api/mock.ts`
  - 本地 mock 的 AI 回复 pending response 同步透传 `confidence`、`reason`、`riskFlags`。

- `frontend/src/types/domain.ts`
  - `TicketReply` 增加只读展示字段：`authorName`、`authorRole`、`isAiGenerated`。

- `frontend/src/components/ai/TicketAiAssistantPanel.test.tsx`
  - 补充 USER 详情页不显示 AI 辅助断言。
  - 补充保存 AI 回复建议时 pending 请求携带 meta 的断言。
  - 为单独渲染的 AI 面板补齐 `QueryClientProvider`。

### Java 后端

- `java/hello-demo/src/main/java/com/example/hello_demo/entity/TicketReply.java`
  - 增加非数据库字段 `authorName`、`authorRole`、`isAiGenerated`。
  - 使用 `@TableField(exist = false)`，不改表结构。

- `java/hello-demo/src/main/java/com/example/hello_demo/service/TicketService.java`
  - `getTicketDetail` 查询或读取缓存后为 replies 补充作者展示信息。
  - `isAiGenerated` 根据 `replyType == AI` 计算。

- `java/hello-demo/src/main/java/com/example/hello_demo/dto/AiReplyPendingRequest.java`
  - pending 保存请求支持 `confidence`、`reason`、`riskFlags`。

- `java/hello-demo/src/main/java/com/example/hello_demo/service/AiPendingActionService.java`
  - 创建 `SAVE_AI_REPLY` pending action 时保留 AI 建议的 meta 信息。
  - 仍然只创建待确认动作，不直接保存正式回复。

- `java/hello-demo/src/test/java/com/example/hello_demo/service/TicketServiceCacheSecurityTest.java`
  - 覆盖详情接口回复作者字段填充。

### Python AI 服务

- `ticket-agent-python/app/schemas/ai_outputs.py`
  - `ReplySuggestionData` 增加固定字段：
    - `intent = GENERATE_REPLY_SUGGESTION`
    - `actionType = AI_REPLY_SUGGESTION`
  - 使用 `Literal` 约束，避免模型输出错误值。

- `ticket-agent-python/app/prompts/reply_suggestion_prompt.py`
  - 回复建议 prompt 明确反幻觉规则。
  - 要求输出固定 `intent` 和 `actionType`。

- `ticket-agent-python/app/services/grounding.py`
  - 通用 grounding prompt 同步要求固定字段。

- `ticket-agent-python/app/clients/llm_client.py`
  - mock 模式也直接返回固定 `intent` 和 `actionType`。

- `ticket-agent-python/tests/test_ticket_ai_service.py`
- `ticket-agent-python/tests/test_structured_ai_outputs.py`
- `ticket-agent-python/tests/test_agent_api.py`
  - 更新回复建议结构化输出契约断言。

## 3. 数据库变更

无数据库结构变更。

本次新增的 Java 字段均为 `@TableField(exist = false)` 展示字段，不需要 migration，不影响现有表。

## 4. 接口链路

### 详情与回复展示

1. 前端进入 `TicketDetailPage`。
2. 调用 `GET /tickets/{id}/detail`。
3. Java `TicketService.getTicketDetail` 读取工单、用户、回复。
4. Java 为每条 `TicketReply` 补充 `authorName`、`authorRole`、`isAiGenerated`。
5. 前端渲染回复列表。

### 普通回复

1. USER 或 STAFF 在详情页提交回复。
2. 前端调用 `POST /tickets/{id}/replies`。
3. 保存成功后刷新 `["ticket-detail", id]` 和 `["tickets"]`。

### AI 回复建议保存

1. STAFF/ADMIN 在详情页点击生成回复建议。
2. Python 返回结构化建议：
   - `suggestion`
   - `confidence`
   - `reason`
   - `risk_flags`
   - `intent = GENERATE_REPLY_SUGGESTION`
   - `actionType = AI_REPLY_SUGGESTION`
3. 前端展示建议，STAFF/ADMIN 可编辑。
4. 点击保存后，前端调用 `POST /tickets/{id}/ai-replies/pending`。
5. Java 创建 `SAVE_AI_REPLY` pending action。
6. 用户确认后，现有 pending confirmation 链路执行保存并刷新详情。
7. 取消时不保存。

## 5. 验证结果

已执行并通过：

- `frontend`: `npm.cmd run lint`
  - 通过；保留既有 Fast Refresh warning。
- `frontend`: `npm.cmd run test -- --run`
  - 6 个测试文件，42 个测试通过。
  - 保留既有 React Router future flag / act warning。
- `frontend`: `npm.cmd run build`
  - TypeScript 编译和 Vite 构建通过。
- `ticket-agent-python`: `.\.venv\Scripts\python.exe -m pytest`
  - 244 个测试通过，1 个既有 Starlette deprecation warning。
- `java/hello-demo`: `.\mvnw.cmd -q -DskipTests compile`
  - 编译通过。
- `java/hello-demo`: `.\mvnw.cmd -q "-Dtest=TicketServiceCacheSecurityTest#ticketDetailReturnsCategoryAndAssignedTo,AiPendingActionServiceTest" test`
  - 本次相关 Java 测试通过。
- 根目录：`docker compose -f docker-compose.prod.yml config --quiet`
  - Compose 配置解析通过。

额外说明：

- 曾执行 `.\mvnw.cmd -q "-Dtest=TicketServiceCacheSecurityTest,AiPendingActionServiceTest" test`，其中 `TicketServiceCacheSecurityTest` 的分类/分配更新用例报 MyBatis-Plus lambda cache 单测环境错误，失败点在既有 `updateTicketCategory` / `updateTicketAssignee` 测试路径，不是本次详情回复链路改动。
- 未启动完整 Docker Compose 运行栈；本次完成了 Compose 配置解析验证。

## 6. 未完成或需确认项

- STAFF “只能回复分配给自己的或未分配工单”这个精细权限，本次未收紧。当前项目已有 STAFF 读取/处理权限模型，本次保持不变，避免扩大改动范围。
- AI 确认后保存的回复类型沿用现有实现 `TicketReplyType.AI`。该回复只有在 STAFF/ADMIN 确认 pending action 后才会入库并对 USER 可见；如果产品上必须显示为 STAFF 正式回复，需要单独调整保存策略和审计语义。
- 当前工作区存在本任务之前已有的脏改动：`.gitignore`、`package-lock.json` 删除、`example.lnk` 删除，以及若干旧报告文件。提交 GitHub 前建议人工确认这些既有变更是否要一起提交。

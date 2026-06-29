# Task 2 实施报告：AI 操作审计日志

## 1. 实施目标

本次任务为 AI 工单闭环补齐审计能力：AI 查询、生成建议、创建待确认动作、确认、取消、失败、无权限等行为，都要进入现有操作日志链路。

本次采用最小改动方案：

- 不新建 `ai_operation_log` 表。
- 不改 `operation_log` 表结构。
- 复用现有 `operation_log.content` 保存 AI 审计扩展字段。
- Java 后端作为最终审计落点。
- Python Agent 只返回稳定审计字段，不直接写数据库。
- 前端复用现有操作日志页面，增加筛选和展示列。

## 2. 项目读取结论

已完成项目读取并输出：

- `TASK2_PROJECT_READING_REPORT.md`

关键结论：

- 当前已有 `operation_log` 表，字段包括 `id/user_id/operation_type/business_type/business_id/content/created_at`。
- 当前没有 `operationSource/actionType/conversationId/resultStatus/targetType/targetId` 独立列。
- 现有日志页原本 STAFF 和 ADMIN 都能看全局日志，不符合本次“全局日志 ADMIN-only”的要求。
- AI 待确认动作已经由 Java 的 `ai_pending_action` 负责，适合作为确认/取消/失败审计的来源。

## 3. 后端实现

### 3.1 OperationLog 复用方式

新增 `OperationLogService.recordAi(...)`，统一写入现有 `operation_log`：

```text
operationType = AI_xxx
businessType = TICKET 或 AI_PENDING_ACTION
businessId = ticketId 或 pendingActionId
content = operationSource=AI; actionType=...; conversationId=...; resultStatus=...
```

AI 审计字段包括：

- `operationSource`
- `actionType`
- `conversationId`
- `intent`
- `toolName`
- `targetType`
- `targetId`
- `resultStatus`
- `requestSummary`
- `resultSummary`
- `riskFlags`

敏感信息仍会脱敏，覆盖 `Bearer`、`authorization`、`token`、`password`、`api_key`、`secret` 等。

### 3.2 AI 操作类型

新增 AI 审计操作类型：

- `AI_QUERY_TICKET`
- `AI_CREATE_TICKET_PENDING`
- `AI_CREATE_TICKET_CONFIRMED`
- `AI_CREATE_TICKET_CANCELLED`
- `AI_UPDATE_STATUS_PENDING`
- `AI_UPDATE_STATUS_CONFIRMED`
- `AI_UPDATE_STATUS_CANCELLED`
- `AI_REPLY_SUGGESTION`
- `AI_REPLY_PENDING`
- `AI_REPLY_CONFIRMED`
- `AI_REPLY_CANCELLED`
- `AI_CLASSIFY_TICKET`
- `AI_ERROR`
- `AI_FORBIDDEN`

保留旧的 AI 操作类型，避免破坏已有代码和历史日志。

### 3.3 审计触发点

已覆盖：

- `/ai/chat` 查询类 AI 响应。
- `/ai/reply-suggestion/{ticketId}` 回复建议生成。
- 创建 AI 待确认动作。
- 确认 AI 待确认动作。
- 取消 AI 待确认动作。
- AI 操作失败。
- AI 操作无权限。

确认和取消日志由 Java 的 `AiPendingActionService` 记录，因为 Java 才知道待确认动作是否真的创建、执行、取消成功。

### 3.4 日志查询增强

`/operation-logs` 增加筛选：

- `operationSource`
- `actionType`
- `resultStatus`
- `conversationId`

兼容旧参数：

- `action`
- `operationType`
- `businessType`
- `ticketId`
- `operatorId`

`OperationLogVO` 增加审计展示字段，并从 `content` 中解析出来返回给前端。

## 4. 权限实现

后端：

- 全局操作日志：仅 ADMIN 可访问。
- 单个工单日志：继续按工单权限控制，STAFF/ADMIN 可看，普通用户不能越权看别人的工单。

前端：

- `/logs` 菜单和全局日志页：仅 ADMIN 显示。
- 工单详情页里的“当前工单日志”：改用单独的 `canViewTicketLogs`，STAFF 仍可查看。

这避免了“前端隐藏按钮等于权限控制”的误区：真正权限仍由后端校验。

## 5. Python Agent 实现

Python `AgentResponse` / `AgentChatResponse` 增加可选审计字段：

- `intent`
- `actionType`
- `riskLevel`
- `toolName`
- `targetType`
- `targetId`
- `requiresConfirmation`
- `error`

Python 不写审计表，只把稳定字段返回 Java。

查询、创建待确认、修改状态待确认、回复建议、保存回复待确认、分类建议、确认、取消、异常都已补齐对应字段。

## 6. 前端实现

日志页新增筛选：

- 操作来源：`ALL / AI / MANUAL`
- AI 操作类型
- 结果状态
- 会话 ID

日志表格新增展示：

- 操作人
- 角色
- 来源
- AI 操作类型
- 会话 ID
- 目标对象
- 结果状态
- 摘要

## 7. 未采用方案

本次没有新建审计表，也没有给 `operation_log` 扩字段。

原因：

- 现有 `operation_log` 已能承载审计记录。
- 本次字段主要用于展示和筛选，放入 `content` 足够。
- 扩表/迁移会增加部署成本和回滚成本。

后续只有在审计字段需要高频统计、复杂聚合或强索引查询时，才建议拆独立字段或建专用表。

## 8. 验证结果

已执行：

```powershell
cd java/hello-demo
.\mvnw.cmd test
```

结果：

```text
Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

已执行：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

结果：

```text
Test Files 6 passed
Tests 42 passed
vite build succeeded
```

前端测试仍有既有 React `act(...)` 警告，不影响本次验证结果。

已执行：

```powershell
cd ticket-agent-python
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m compileall app
```

结果：

```text
244 passed, 1 warning
compileall succeeded
```

## 9. 主要修改文件

Java：

- `java/hello-demo/src/main/java/com/example/hello_demo/service/OperationLogService.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/service/AiService.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/service/AiPendingActionService.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/controller/OperationLogController.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/vo/OperationLogVO.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/enums/OperationType.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/security/PermissionUtil.java`

Python：

- `ticket-agent-python/app/schemas/agent_response.py`
- `ticket-agent-python/app/schemas/agent_schema.py`
- `ticket-agent-python/app/api/agent_api.py`
- `ticket-agent-python/app/services/agent_tool_service.py`
- `ticket-agent-python/app/services/response_builder.py`

Frontend：

- `frontend/src/pages/LogsPage.tsx`
- `frontend/src/pages/TicketDetailPage.tsx`
- `frontend/src/auth/permissions.ts`
- `frontend/src/api/client.ts`
- `frontend/src/types/domain.ts`
- `frontend/src/i18n/messages.ts`

测试：

- `java/hello-demo/src/test/java/com/example/hello_demo/service/AiServiceAuditTest.java`
- `java/hello-demo/src/test/java/com/example/hello_demo/service/OperationLogServiceTest.java`
- `frontend/src/auth/permissions.test.ts`
- `ticket-agent-python/tests/test_agent_api.py`
- `ticket-agent-python/tests/test_human_confirmation.py`
- `ticket-agent-python/tests/test_structured_ai_outputs.py`

## 10. 终端式总结

```text
TASK2 AI_OPERATION_AUDIT_LOG

read project      OK
reading report    OK  TASK2_PROJECT_READING_REPORT.md
reuse operation_log OK
new audit table   NO
java audit sink   OK
python audit fields OK
frontend filters  OK
admin-only global logs OK
staff ticket logs OK
sensitive masking OK

java tests        OK  95 passed
frontend tests    OK  42 passed
frontend build    OK
python tests      OK  244 passed
python compile    OK
```


# 任务 2 项目现状阅读报告：AI 操作审计日志

生成时间：2026-06-29  
范围：前端日志页、Java `operation_log` / AI / pending_action、Python Agent 响应结构。  
原则：先复用现有 `operation_log`，不新建复杂 AI 日志系统。

## 1. 是否已有 operation_log 表或类似日志表

已有。Java 后端存在 `operation_log` 表、`OperationLog` 实体、`OperationLogMapper`、`OperationLogService`、`OperationLogController`。

## 2. operation_log 当前字段

当前 SQL 字段：

- `id`
- `user_id`
- `operation_type`
- `business_type`
- `business_id`
- `content`
- `created_at`

当前没有独立的 `operationSource`、`actionType`、`conversationId`、`resultStatus`、`targetType`、`targetId` 字段。

## 3. 是否能区分 MANUAL / AI

能部分区分。当前 AI 操作使用 `operation_type` 中的 `AI_*` 前缀；普通操作使用 `CREATE_TICKET`、`UPDATE_TICKET_STATUS` 等。没有独立 `operationSource` 字段。

最小方案：不改表，用 `operation_type` 是否以 `AI_` 派生 `operationSource=AI/MANUAL`。

## 4. 是否能记录 actionType

能。当前 `operation_type` 可以承载 actionType。

缺口：现有 AI actionType 命名不完全覆盖任务要求，例如没有明确的 `AI_CREATE_TICKET_PENDING`、`AI_UPDATE_STATUS_CANCELLED`、`AI_ERROR`、`AI_FORBIDDEN`。

## 5. 是否能记录 conversationId

能部分记录。当前部分 AI 日志把 `conversationId=...` 写在 `content` 文本里。没有独立字段。

最小方案：继续写入 `content`，并在 VO 层从 `content` 提取 `conversationId`。

## 6. 是否能记录 targetType / targetId

能部分记录。当前 `business_type` / `business_id` 可以表示目标对象，例如 `TICKET#1`、`AI_PENDING_ACTION#10`。

缺口：前端 VO 只暴露了 `ticketId`，没有暴露通用 `targetType` / `targetId`。

## 7. 是否能记录 resultStatus

能部分记录。当前成功日志较多，但没有统一 `SUCCESS/FAILED/CANCELLED/FORBIDDEN` 字段。

最小方案：不改表，新增统一日志内容格式 `resultStatus=...`，旧日志按 operationType 推断。

## 8. 是否能记录失败原因

能。当前 `content` 可记录失败原因，`OperationLogService.safeDetail()` 会做敏感字段过滤和 500 字符截断。

缺口：AI 失败、权限不足并非所有路径都会主动记录。

## 9. Java 当前已写入操作日志的位置

- `AuthService`：注册、登录成功、登录失败。
- `TicketService`：创建工单、修改状态、修改分类、分配处理人、删除工单。
- `TicketReplyService`：用户/STAFF/AI 回复。
- `AiService`：AI 查询、AI 回复建议、部分确认/取消文本审计。
- `AiPendingActionService`：创建 pending_action、确认、取消、保存 AI 回复、采纳分类。

## 10. AI 查询工单是否有日志

有，但依赖 Java `AiService` 对用户输入文本做启发式判断。Python 当前响应没有稳定返回 `intent/actionType/toolName/targetType/targetId`，因此 Java 无法可靠按 Python 实际意图记录。

## 11. AI 创建工单 pending 是否有日志

有通用日志：`AI_PENDING_ACTION_CREATED`。但没有专用 `AI_CREATE_TICKET_PENDING`。

## 12. AI 创建工单确认 / 取消是否有日志

有通用日志：`AI_WRITE_CONFIRMED`、`AI_WRITE_CANCELLED`。但没有按创建工单细分的 `AI_CREATE_TICKET_CONFIRMED`、`AI_CREATE_TICKET_CANCELLED`。

## 13. AI 修改状态 pending 是否有日志

有通用日志：`AI_PENDING_ACTION_CREATED`。但没有专用 `AI_UPDATE_STATUS_PENDING`。

## 14. AI 修改状态确认 / 取消是否有日志

有通用日志：`AI_WRITE_CONFIRMED`、`AI_WRITE_CANCELLED`。但没有按状态修改细分的 `AI_UPDATE_STATUS_CONFIRMED`、`AI_UPDATE_STATUS_CANCELLED`。

## 15. AI 回复建议是否有日志

有。`AiService.generateReplySuggestion()` 会记录 `AI_REPLY_SUGGESTED`。

缺口：`/agent/chat` 中通过意图生成回复建议时，需要 Python 返回可审计字段，Java 才能稳定记为 `AI_REPLY_SUGGESTION`。

## 16. AI 回复保存 pending 是否有日志

有。`AiPendingActionService.createSaveAiReplyPending()` 走 `createPendingAction()`，并额外记录 `AI_REPLY_SAVE_PENDING_CREATED`。

## 17. AI 回复保存确认 / 取消是否有日志

确认：有 `AI_WRITE_CONFIRMED`，且 `TicketReplyService.createAiReply()` 会记录 AI 回复。  
取消：有 `AI_WRITE_CANCELLED` 和 `AI_ACTION_CANCELLED`。

缺口：缺少任务要求的统一 actionType：`AI_REPLY_CONFIRMED`、`AI_REPLY_CANCELLED`。

## 18. Python 响应是否包含 intent / actionType / riskFlags / toolName / targetType / targetId

当前只稳定包含：

- `type`
- `message`
- `data`
- `risk_flags`

不稳定或缺失：

- `intent`
- `actionType`
- `riskLevel`
- `toolName`
- `targetType`
- `targetId`
- `requiresConfirmation`

## 19. 前端是否已有日志页

已有。`frontend/src/pages/LogsPage.tsx` 是全局日志页；工单详情页也能查看当前工单日志。

## 20. 当前日志页是否支持筛选

支持部分筛选：

- `ticketId`
- `operatorId`
- `action`
- `page`
- `size`

缺少：

- `operationSource`
- AI 操作类型筛选
- `resultStatus`
- `conversationId`

## 21. 当前日志页是否只允许 ADMIN 访问

否。前端 `canViewOperationLogs()` 当前允许 STAFF 和 ADMIN；Java `PermissionUtil.canViewGlobalOperationLogs()` 也允许 STAFF 和 ADMIN。任务要求全局日志页只允许 ADMIN，因此需要收紧。

工单详情内的当前工单日志仍可允许 STAFF 查看，这是局部业务日志，不等同全局日志页。

## 22. 是否存在敏感信息泄露风险

已有防护：

- Java `OperationLogService.safeDetail()` 会过滤 `authorization/auth_token/token/password`。
- Java pending_action payload 会拒绝 token/authorization。
- Python 工具日志会隐藏 `auth_token`。

缺口：

- Java 日志过滤未覆盖 `apiKey/api_key/secret/Bearer xxx` 等关键词。
- AI 响应摘要如果直接记录大文本，需要截断并清洗。

## 23. 当前缺口列表

1. 全局日志权限应从 STAFF/ADMIN 收紧为 ADMIN。
2. 日志查询接口缺少 `operationSource`、`resultStatus`、`conversationId` 筛选。
3. `OperationLogVO` 缺少 `username/role/operationSource/actionType/conversationId/targetType/targetId/resultStatus/requestSummary/resultSummary`。
4. Python Agent 响应缺少稳定审计字段。
5. AI pending_action 的创建、确认、取消需要专用 actionType。
6. AI 失败和权限不足需要稳定记录为 `FAILED/FORBIDDEN`。
7. Java 敏感字段过滤需要覆盖 `Bearer`、`apiKey/api_key`、`secret`。

## 24. 最小修改方案

不新增 `ai_operation_log`，不改现有表结构。理由：

- `operation_log.content` 可承载 `conversationId/resultStatus/intent/toolName/requestSummary/resultSummary`。
- `operation_type/business_type/business_id` 已能承载 `actionType/targetType/targetId`。
- 新增表会带来迁移、查询页、权限和数据一致性成本，当前不是必须。

最小实现：

1. Java 增加 AI actionType 枚举常量。
2. Java `OperationLogService` 增加 `recordAi(...)` 方法，把审计字段写入 `content`。
3. Java `OperationLogVO` 增加派生字段，旧字段保留，前端兼容。
4. Java 日志查询增加 `operationSource/resultStatus/conversationId` 过滤。
5. Java 全局日志权限改为仅 ADMIN。
6. Java `AiService` 根据 Python 返回的审计字段记录成功、失败、权限不足。
7. Java `AiPendingActionService` 对 pending/confirm/cancel 写专用 AI 审计日志。
8. Python `AgentResponse` 增加可选审计字段，并在关键响应中填充。
9. 前端保留现有日志页，增加筛选控件和展示列。
10. 增加或更新最小测试，覆盖权限、过滤、AI 审计字段。

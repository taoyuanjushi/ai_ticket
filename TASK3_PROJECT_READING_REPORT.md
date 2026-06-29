# Task 3 项目现状读取报告：管理员 Dashboard

## 1. 当前是否已有 ADMIN 首页

没有独立的 ADMIN 首页。当前 ADMIN 可访问 `/users` 和 `/settings`，其中 `/settings` 是系统概览页，不是统计 Dashboard。

## 2. 当前是否已有 Dashboard 页面

没有发现 `DashboardPage`、`DashboardController` 或 dashboard API。

## 3. 当前 ADMIN 首页路由是什么

当前没有明确 ADMIN 首页路由。ADMIN 相关入口包括：

- `/users`
- `/settings`
- `/logs`

## 4. 当前 USER / STAFF / ADMIN 路由权限如何控制

前端通过 `RequireRole` 和 `auth/permissions.ts` 控制页面入口。

- `canViewAdminArea`：仅 ADMIN。
- `canViewOperationLogs`：仅 ADMIN。
- `canViewTicketLogs`：STAFF / ADMIN。

后端通过 `CurrentUserContext`、`JwtInterceptor`、`PermissionUtil` 和 Service 层校验控制真实权限。

## 5. 当前是否已有 dashboard API

没有。

## 6. 当前是否已有 admin API

没有 `/admin/**` 统计类 API。现有用户管理接口是 `/users`。

## 7. 当前 ticket 表有哪些状态

建表和种子数据使用：

- `OPEN`
- `PROCESSING`
- `CLOSED`

## 8. 当前 ticket 表有哪些优先级

代码允许：

- `LOW`
- `MEDIUM`
- `HIGH`
- `URGENT`

SQL 注释较旧，只写到 `HIGH`，但当前 Java 和前端都已经支持 `URGENT`。

## 9. 当前工单状态枚举有哪些值

`TicketStatus` 当前值：

- `OPEN`
- `PROCESSING`
- `CLOSED`

没有 `PENDING` 和 `DONE`。

## 10. 当前优先级枚举有哪些值

没有独立 `TicketPriority` Java 枚举，`TicketService` 使用允许集合：

- `LOW`
- `MEDIUM`
- `HIGH`
- `URGENT`

前端 `TicketPriority` 类型也是这四个值。

## 11. 当前 AI 建议生成记录在哪里

AI 回复建议生成由 Java `AiService.generateReplySuggestion` 记录到 `operation_log`，操作类型为：

- `AI_REPLY_SUGGESTION`

## 12. 当前 AI 建议采纳记录在哪里

AI 回复建议保存走 Java pending_action 确认链路，确认后记录到 `operation_log`，操作类型为：

- `AI_REPLY_CONFIRMED`

同时 `TicketReplyService.createAiReply` 会保存 `ticket_reply.reply_type = AI`。

## 13. 当前 operation_log 是否记录 AI_REPLY_SUGGESTION

是。

## 14. 当前 operation_log 是否记录 AI_REPLY_CONFIRMED

是，来自 `AiPendingActionService` 的确认审计。

## 15. 当前 ticket_reply 是否使用 TicketReplyType.AI

是。`TicketReplyType` 包含：

- `USER`
- `STAFF`
- `AI`

## 16. 当前是否可以从真实数据库统计 Dashboard 数据

可以。

- 工单数量可从 `ticket` 表按 `status`、`priority` 做 `COUNT`。
- AI 建议生成和采纳可从 `operation_log.operation_type` 做 `COUNT`。

## 17. 当前缺口列表

- 没有 Dashboard 页面。
- 没有 Dashboard 导航入口。
- 没有 `/admin/dashboard/stats` API。
- 没有 DashboardStats VO。
- 没有 DashboardService。
- 前端 mock API 没有 Dashboard 统计。
- 状态体系没有 `PENDING` / `DONE`，需要在 Dashboard 中兼容当前真实状态。

## 18. 最小修改方案

采用方案 B：新建 `/admin/dashboard` 页面。

原因：当前没有真正 ADMIN 首页，`/settings` 只是系统概览，占用它会混淆页面职责。

最小实现：

- Java 新增 `AdminDashboardController`、`DashboardService`、`DashboardStatsVO`。
- 使用 MyBatis-Plus `selectCount` 在数据库内聚合。
- `pendingCount` 统计当前真实待处理状态 `OPEN`，并兼容可能存在的 `PENDING`。
- `doneCount` 统计 `DONE`，当前系统没有该状态时返回 0。
- AI 统计来自 `operation_log.operation_type`。
- 前端新增 `DashboardPage`，只放数字卡片，不引入图表库。
- 前端新增 `api.getDashboardStats()`。
- 只有 ADMIN 显示入口，USER / STAFF 直接访问路由跳转无权限。
- Python 不修改。


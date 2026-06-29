# Task 3 实施报告：管理员 Dashboard

## 1. 本次修改了哪些文件

Task 3 相关文件：

- `TASK3_PROJECT_READING_REPORT.md`
- `TASK3_IMPLEMENTATION_REPORT.md`
- `java/hello-demo/src/main/java/com/example/hello_demo/controller/AdminDashboardController.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/service/DashboardService.java`
- `java/hello-demo/src/main/java/com/example/hello_demo/vo/DashboardStatsVO.java`
- `java/hello-demo/src/test/java/com/example/hello_demo/controller/AdminDashboardControllerTest.java`
- `java/hello-demo/src/test/java/com/example/hello_demo/service/DashboardServiceTest.java`
- `frontend/src/pages/DashboardPage.tsx`
- `frontend/src/App.tsx`
- `frontend/src/App.test.tsx`
- `frontend/src/components/AppShell.tsx`
- `frontend/src/api/client.ts`
- `frontend/src/api/mock.ts`
- `frontend/src/types/domain.ts`
- `frontend/src/i18n/messages.ts`

## 2. 前端改了什么

新增 `/admin/dashboard` 页面，展示 10 个数字卡片：

- 总工单
- 待处理
- 处理中
- 已完成
- 已关闭
- 高优先级
- 紧急优先级
- AI 建议
- AI 采纳
- AI 采纳率

复用已有 `StatCard`、`Loading`、`ErrorNotice`、`PageHeader`，没有引入图表库或新 UI 库。

## 3. Java 后端改了什么

新增只读统计接口：

```text
GET /admin/dashboard/stats
```

新增 `DashboardService`，使用 MyBatis-Plus `selectCount` 在数据库中统计，不把所有工单查到 Java 内存里循环。

## 4. Python 是否修改

没有修改 Python。

## 5. Dashboard 页面采用方案 A 还是方案 B

采用方案 B：新建 `/admin/dashboard` 页面。

## 6. 为什么选择该方案

当前项目没有真正 ADMIN 首页；`/settings` 是系统概览页，`/users` 是用户管理页。新建独立 Dashboard 页面改动更小、职责更清楚。

## 7. 新增或复用的前端 API

新增：

```ts
api.getDashboardStats()
```

请求：

```text
GET /admin/dashboard/stats
```

## 8. 新增的 Java 接口

```text
GET /admin/dashboard/stats
```

返回统一 `Result<DashboardStatsVO>`。

## 9. 接口权限如何控制

`DashboardService.getStats()` 入口调用：

```java
PermissionUtil.requireAdmin();
```

ADMIN 允许访问，USER / STAFF 返回 403。

## 10. DashboardStatsVO / DTO 字段说明

`DashboardStatsVO` 是 Java record，字段包括：

- `ticketTotal`
- `pendingCount`
- `processingCount`
- `doneCount`
- `closedCount`
- `highPriorityCount`
- `urgentPriorityCount`
- `aiSuggestionCount`
- `aiAcceptedCount`
- `aiAcceptanceRate`

## 11. ticketTotal 如何统计

从 `ticket` 表执行 `COUNT`。

## 12. pendingCount 如何统计

当前系统真实待处理状态是 `OPEN`，因此统计：

```text
status IN ('OPEN', 'PENDING')
```

这样兼容当前数据，也兼容未来可能出现的 `PENDING`。

## 13. processingCount 如何统计

从 `ticket` 表统计：

```text
status = 'PROCESSING'
```

## 14. doneCount 如何统计

从 `ticket` 表统计：

```text
status = 'DONE'
```

当前系统没有 `DONE` 枚举，所以正常返回 0。

## 15. closedCount 如何统计

从 `ticket` 表统计：

```text
status = 'CLOSED'
```

## 16. highPriorityCount 如何统计

从 `ticket` 表统计：

```text
priority = 'HIGH'
```

## 17. urgentPriorityCount 如何统计

从 `ticket` 表统计：

```text
priority = 'URGENT'
```

## 18. aiSuggestionCount 从哪里统计

从 `operation_log` 表统计：

```text
operation_type = 'AI_REPLY_SUGGESTION'
```

## 19. aiAcceptedCount 从哪里统计

从 `operation_log` 表统计：

```text
operation_type = 'AI_REPLY_CONFIRMED'
```

## 20. aiAcceptanceRate 如何计算

```text
aiAcceptedCount / aiSuggestionCount
```

如果 `aiSuggestionCount = 0`，返回 `0.0`。

## 21. 没有数据时如何处理

后端 `COUNT` 结果为空时按 0 处理。

前端对 `null` / `undefined` 也按 0 展示。

## 22. USER / STAFF 为什么不能访问

Dashboard 是全局统计，包含所有工单和 AI 行为汇总，属于管理员视角。USER / STAFF 不应看到全局运营数据。

## 23. 是否使用真实数据库聚合

是。Java 使用 MyBatis-Plus `selectCount` 让数据库执行 `COUNT`。

## 24. 是否引入图表库

没有。

## 25. 是否修改数据库表

没有。

## 26. 是否修改 Python

没有。

## 27. 验收清单完成情况

已自动验证：

- ADMIN 调用 `/admin/dashboard/stats` 返回 200。
- USER 调用 `/admin/dashboard/stats` 返回 403。
- STAFF 调用 `/admin/dashboard/stats` 返回 403。
- 前端 ADMIN 可访问 `/admin/dashboard`。
- 前端 STAFF 直接访问 `/admin/dashboard` 会进入无权限页。
- 前端 build 通过。
- Java test/package 通过。
- 没有引入图表库。
- 没有修改 Python。

需在 Docker 或真实环境人工确认：

- ADMIN 登录后实际看到统计卡片。
- 新建工单后 `ticketTotal` 刷新正确。
- 修改状态后各状态卡片刷新正确。
- 生成 AI 回复建议后 `aiSuggestionCount` 增加。
- 确认保存 AI 回复建议后 `aiAcceptedCount` 增加。

## 28. 未完成或需人工确认的问题

Docker Compose 命令可用，但 Docker 引擎未运行：

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

因此本次没有执行全栈 Docker 验收。

另外，当前系统状态枚举只有 `OPEN / PROCESSING / CLOSED`，没有 `PENDING / DONE`。本次没有改状态体系：

- `pendingCount` 用 `OPEN` 表示待处理。
- `doneCount` 保留字段，当前通常为 0。

## 终端式总结

```text
modified files:
  frontend/src/App.tsx
  frontend/src/App.test.tsx
  frontend/src/api/client.ts
  frontend/src/api/mock.ts
  frontend/src/components/AppShell.tsx
  frontend/src/i18n/messages.ts
  frontend/src/pages/DashboardPage.tsx
  frontend/src/types/domain.ts
  java/hello-demo/src/main/java/com/example/hello_demo/controller/AdminDashboardController.java
  java/hello-demo/src/main/java/com/example/hello_demo/service/DashboardService.java
  java/hello-demo/src/main/java/com/example/hello_demo/vo/DashboardStatsVO.java
  java/hello-demo/src/test/java/com/example/hello_demo/controller/AdminDashboardControllerTest.java
  java/hello-demo/src/test/java/com/example/hello_demo/service/DashboardServiceTest.java
  TASK3_PROJECT_READING_REPORT.md
  TASK3_IMPLEMENTATION_REPORT.md

new api:
  GET /admin/dashboard/stats

dashboard path:
  /admin/dashboard

permission:
  ADMIN 200
  USER 403
  STAFF 403

data source:
  ticket
  operation_log

verification:
  java: .\mvnw.cmd test
  java: .\mvnw.cmd -DskipTests package
  frontend: npm.cmd run test
  frontend: npm.cmd run build

next:
  start Docker Desktop and run full-stack acceptance if needed
```


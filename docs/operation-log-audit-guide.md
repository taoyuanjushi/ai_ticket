# OperationLog 审计日志说明

## OperationLog 是什么

`OperationLog` 是业务审计日志，用来记录谁在什么时间对系统做了什么业务操作，例如创建工单、修改状态、回复工单、确认 AI 写操作。

它和普通程序日志不同：

- 普通程序日志主要给开发人员排查运行错误，例如异常堆栈、SQL 调试信息。
- 操作日志主要给业务和管理员追踪行为，例如哪个用户修改了哪个工单。

操作日志不能返回 token、密码、内部异常堆栈等敏感信息。

## 当前返回字段

```json
{
  "id": 1,
  "ticketId": 3,
  "operatorId": 2,
  "operatorName": "Staff One",
  "action": "UPDATE_TICKET_STATUS",
  "detail": "Ticket #3 changed to PROCESSING",
  "createdAt": "2026-06-24T10:00:00"
}
```

`operatorName` 来自 `user` 表；如果找不到用户，会回退为 `user#用户ID`。

## 新增接口

### 查看某个工单的操作日志

```http
GET /tickets/{id}/logs?page=1&size=20
```

规则：

- 必须登录。
- `USER` 不能查看操作日志，返回 403。
- `STAFF` 可以查看当前项目权限范围内的工单日志。
- `ADMIN` 可以查看任意工单日志。
- 工单不存在返回 404。
- 返回按 `createdAt` 倒序。

### 查看全局操作日志

```http
GET /operation-logs?page=1&size=20&ticketId=3&operatorId=2&action=UPDATE_TICKET_STATUS
```

过滤参数：

- `ticketId`：按工单过滤。
- `operatorId`：按操作者过滤。
- `action`：按操作类型过滤。

当前接口也兼容旧参数 `userId`、`operationType`、`businessType`，但新页面使用 `ticketId/operatorId/action`。

权限规则：

- `USER`：不能查看全局操作日志，返回 403。
- `STAFF`：只能查看工单和工单回复相关日志。
- `ADMIN`：可以查看全部日志。

## 分页规则

- `page` 从 1 开始，默认 1。
- `size` 默认 20。
- `size` 最大 100。
- `page < 1` 返回 400。
- `size < 1` 返回 400。
- `size > 100` 返回 400。

日志不能一次返回全部，因为操作日志会持续增长。必须使用数据库分页，避免日志量变大后拖慢 Java 服务和数据库。

## 前端入口

- 工单详情页：`STAFF` / `ADMIN` 可点击“查看当前工单日志”懒加载当前工单日志。
- 全局日志页：`STAFF` / `ADMIN` 可从侧边栏进入。
- `USER` 看不到日志入口。
- 后端仍然是最终权限保障，前端隐藏按钮只是体验优化。

## 测试命令

Java：

```powershell
cd java/hello-demo
.\mvnw.cmd test
```

如果没有 `mvnw.cmd`：

```powershell
mvn test
```

前端：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

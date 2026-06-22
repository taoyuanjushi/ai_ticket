# 最终端到端 Postman 验收指南

本文固定 Java + Python AI + 前端当前阶段的接口验收顺序。AI 主链路必须从 Java 进入：

```text
Postman / 前端
-> Java /ai/chat
-> Python /agent/chat
-> Java Ticket API
-> MySQL / Redis
```

`/agent/chat` 可以用于 Python 服务自测，但最终联调验收以 Java `/ai/chat` 为准。

## 1. 导入 Postman 文件

```text
postman/stage5-full-integration.postman_collection.json
postman/stage5-local.postman_environment.json
```

导入步骤：

1. 打开 Postman。
2. 点击 `Import`。
3. 导入 collection。
4. 再导入 environment。
5. 右上角选择 `Stage5 Local Integration`。

建议环境变量：

| 变量名 | 示例 | 说明 |
|---|---|---|
| `javaBaseUrl` | `http://127.0.0.1:8080` | Java 后端地址 |
| `pythonBaseUrl` | `http://127.0.0.1:8001` | 仅用于 Python health 自测 |
| `tomToken` | 登录后填写 | tom JWT |
| `aliceToken` | 登录后填写 | alice JWT |
| `staffToken` | 登录后填写 | staff JWT |
| `adminToken` | 登录后填写 | admin JWT |
| `tomTicketId` | SQL 查询后填写 | `验收-登录失败` 工单 ID |
| `aliceTicketId` | SQL 查询后填写 | `验收-文件上传失败` 工单 ID |
| `conversationId` | `postman-final-001` | AI 会话 ID |

不要把真实 token、真实 LLM Key 写进 collection 文件。

## 2. 启动前准备

按总 README 启动：

```text
1. MySQL
2. Redis
3. Java 8080
4. Python 8001
```

导入数据库表和测试数据：

```text
java/hello-demo/src/main/resources/sql/user.sql
java/hello-demo/src/main/resources/sql/ticket.sql
java/hello-demo/src/main/resources/sql/ticket_reply.sql
java/hello-demo/src/main/resources/sql/operation_log.sql
java/hello-demo/src/main/resources/sql/ai_pending_action.sql
docs/stage5-test-data.sql
```

确认测试账号：

```sql
SELECT id, username, role
FROM user
WHERE username IN ('tom', 'alice', 'staff', 'admin')
ORDER BY username;
```

登录明文密码均为：

```text
123456
```

确认测试工单：

```sql
SELECT id, title, user_id, status, priority
FROM ticket
WHERE title IN ('验收-登录失败', '验收-文件上传失败')
ORDER BY id;
```

把两个 ID 分别填入 Postman 环境变量 `tomTicketId`、`aliceTicketId`。

## 3. 基础健康检查

### 3.1 Java Health

```http
GET {{javaBaseUrl}}/hello
```

预期：HTTP 200。

### 3.2 Python Health

```http
GET {{pythonBaseUrl}}/health
```

预期：

```json
{
  "status": "ok",
  "service": "ticket-agent-python"
}
```

## 4. 登录并保存 Token

### 4.1 登录 tom

```http
POST {{javaBaseUrl}}/auth/login
Content-Type: application/json
```

```json
{
  "username": "tom",
  "password": "123456"
}
```

预期：HTTP 200，`data.role` 为 `USER`。把 `data.token` 保存为 `tomToken`。

### 4.2 登录 alice

Body：

```json
{
  "username": "alice",
  "password": "123456"
}
```

预期：`data.role` 为 `USER`。保存 `aliceToken`。

### 4.3 登录 staff

Body：

```json
{
  "username": "staff",
  "password": "123456"
}
```

预期：`data.role` 为 `STAFF`。保存 `staffToken`。

### 4.4 登录 admin

Body：

```json
{
  "username": "admin",
  "password": "123456"
}
```

预期：`data.role` 为 `ADMIN`。保存 `adminToken`。

## 5. tom 权限验收

### 5.1 tom 查询自己的工单

```http
GET {{javaBaseUrl}}/tickets
Authorization: Bearer {{tomToken}}
```

预期：

- HTTP 200。
- 结果包含 `验收-登录失败`。
- 不包含 `验收-文件上传失败`。

数据库辅助检查：

```sql
SELECT t.id, t.title, u.username
FROM ticket t
JOIN user u ON t.user_id = u.id
WHERE u.username IN ('tom', 'alice');
```

### 5.2 tom 尝试看 alice 工单详情

```http
GET {{javaBaseUrl}}/tickets/{{aliceTicketId}}/detail
Authorization: Bearer {{tomToken}}
```

预期：

- HTTP 403 或 404，按当前后端隐藏资源策略为准。
- 不能返回 alice 工单详情。

### 5.3 tom 尝试修改状态

```http
PUT {{javaBaseUrl}}/tickets/{{tomTicketId}}/status
Authorization: Bearer {{tomToken}}
Content-Type: application/json
```

```json
{
  "status": "PROCESSING"
}
```

预期：

- HTTP 403。
- message 为 `你没有权限执行该操作。` 或等价权限失败提示。
- 数据库状态仍为 `OPEN`。

数据库验证：

```sql
SELECT id, title, status
FROM ticket
WHERE id = {{tomTicketId}};
```

## 6. staff AI 查询验收

### 6.1 staff 使用 AI 查询工单

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "查询工单",
  "conversationId": "postman-query-001"
}
```

预期：

- HTTP 200。
- 响应来自 Java `/ai/chat`。
- Python 通过 Java Ticket API 查询。
- 返回可读工单列表。

## 7. staff AI 创建工单 pending + confirm

### 7.1 发起创建

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "帮我创建一个工单，标题是验收-AI创建工单，描述是用于验证AI pending创建流程，优先级高",
  "conversationId": "postman-create-001"
}
```

预期：

- HTTP 200。
- 返回确认提示。
- 不应立即新增 `ticket`。
- `ai_pending_action` 新增一条 `PENDING`，`action_type=CREATE_TICKET`。
- `payload_json` 不包含 token。

数据库验证：

```sql
SELECT id, user_id, conversation_id, action_type, status, payload_json
FROM ai_pending_action
WHERE conversation_id = 'postman-create-001'
ORDER BY id DESC;
```

### 7.2 确认创建

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "确认",
  "conversationId": "postman-create-001"
}
```

预期：

- HTTP 200。
- 返回“已创建工单”。
- `ai_pending_action.status=CONFIRMED`。
- `ticket` 表新增 `验收-AI创建工单`。
- `operation_log` 有 AI 确认写操作记录。

数据库验证：

```sql
SELECT id, title, status, priority, user_id
FROM ticket
WHERE title = '验收-AI创建工单'
ORDER BY id DESC;

SELECT id, status, confirmed_at
FROM ai_pending_action
WHERE conversation_id = 'postman-create-001'
ORDER BY id DESC;
```

### 7.3 再次确认，验证幂等

重复发送 7.2。

预期：

- 不再创建第二张工单。
- 返回“当前没有待确认的操作”或等价提示。

数据库验证：

```sql
SELECT COUNT(*) AS cnt
FROM ticket
WHERE title = '验收-AI创建工单';
```

## 8. staff AI 修改状态 pending + confirm

### 8.1 发起修改

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "把 {{tomTicketId}} 号工单改成处理中",
  "conversationId": "postman-update-001"
}
```

预期：

- HTTP 200。
- 返回确认提示。
- `ticket.status` 仍未变化。
- `ai_pending_action` 新增 `UPDATE_TICKET_STATUS` 的 `PENDING` 记录。

### 8.2 确认修改

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "确认",
  "conversationId": "postman-update-001"
}
```

预期：

- HTTP 200。
- 工单状态从 `OPEN` 变为 `PROCESSING`。
- 再从 `CLOSED` 回到 `OPEN` 这类非法流转必须失败。

数据库验证：

```sql
SELECT id, title, status
FROM ticket
WHERE id = {{tomTicketId}};
```

## 9. staff 取消写操作

### 9.1 发起一个待取消动作

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "帮我创建一个工单，标题是验收-取消后不应创建，描述是用于验证取消流程，优先级中",
  "conversationId": "postman-cancel-001"
}
```

预期：返回确认提示。

### 9.2 取消

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "取消",
  "conversationId": "postman-cancel-001"
}
```

预期：

- 返回“已取消”。
- `ai_pending_action.status=CANCELLED`。
- `ticket` 表没有 `验收-取消后不应创建`。

数据库验证：

```sql
SELECT COUNT(*) AS cnt
FROM ticket
WHERE title = '验收-取消后不应创建';
```

## 10. AI 回复建议 JSON

### 10.1 生成回复建议

```http
POST {{javaBaseUrl}}/ai/tickets/{{tomTicketId}}/reply-suggestion
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

预期：

- HTTP 200。
- 返回结构化 JSON。
- 至少包含：

```json
{
  "suggestion": "...",
  "confidence": 0.8,
  "reason": "...",
  "risk_flags": []
}
```

- 无权限工单不能生成建议。
- 生成建议不会自动写入 `ticket_reply`。

数据库验证：

```sql
SELECT COUNT(*) AS ai_reply_count
FROM ticket_reply
WHERE ticket_id = {{tomTicketId}} AND reply_type = 'AI';
```

### 10.2 保存 AI 回复建议必须走 pending_action

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer {{staffToken}}
Content-Type: application/json
```

```json
{
  "message": "保存 {{tomTicketId}} 号工单的 AI 回复建议，内容是建议用户补充错误截图和发生时间。",
  "conversationId": "postman-save-ai-reply-001"
}
```

预期：

- 返回确认提示。
- `ai_pending_action.action_type=SAVE_AI_REPLY`。
- 还没有新增 `ticket_reply.reply_type=AI`。

确认：

```json
{
  "message": "确认",
  "conversationId": "postman-save-ai-reply-001"
}
```

预期：

- 新增 `ticket_reply.reply_type=AI`。
- `operation_log` 记录 AI 写操作确认。

数据库验证：

```sql
SELECT id, ticket_id, user_id, reply_type, content
FROM ticket_reply
WHERE ticket_id = {{tomTicketId}} AND reply_type = 'AI'
ORDER BY id DESC;
```

## 11. token 错误或过期

```http
POST {{javaBaseUrl}}/ai/chat
Authorization: Bearer invalid-token
Content-Type: application/json
```

```json
{
  "message": "查询工单",
  "conversationId": "postman-invalid-token-001"
}
```

预期：

- HTTP 401。
- message 为 `登录状态已失效，请重新登录。` 或等价提示。
- 不创建 pending_action。
- 不执行业务写操作。

## 12. admin 日志验收

```http
GET {{javaBaseUrl}}/operation-logs
Authorization: Bearer {{adminToken}}
```

预期：HTTP 200，能看到登录、AI pending、AI 确认、AI 取消等日志。

tom 查询日志：

```http
GET {{javaBaseUrl}}/operation-logs
Authorization: Bearer {{tomToken}}
```

预期：HTTP 403。

## 13. 最终通过标准

全部通过时应满足：

- 前端和 Postman 的 AI 主链路都走 Java `/ai/chat`。
- tom 只能看自己的工单，不能修改状态，不能看日志。
- staff 创建、修改、保存 AI 回复建议都先 pending，再确认。
- 确认使用当前 token，重复确认不重复执行。
- 取消不会执行业务写操作。
- `ai_pending_action.payload_json` 不保存 token。
- 回复建议返回 `suggestion`、`confidence`、`reason`、`risk_flags`。
- `ticket_reply.reply_type='AI'` 只在确认保存后出现。

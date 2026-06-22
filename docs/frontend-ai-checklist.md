# 前端 AI 页面最终验收清单

本清单用于浏览器手工验收。前端 AI 请求必须只走 Java 后端：

```text
浏览器 -> /api/ai/chat -> Java /ai/chat -> Python /agent/chat
```

不要在生产请求中直接访问：

```text
http://127.0.0.1:8001/agent/chat
```

## 1. 启动前准备

1. MySQL 已启动。
2. Redis 已启动。
3. 已导入 `docs/stage5-test-data.sql`。
4. Java 后端运行在 `http://127.0.0.1:8080`。
5. Python AI 服务运行在 `http://127.0.0.1:8001`。
6. 前端运行在 Vite 默认地址，通常是 `http://127.0.0.1:5173`。

前端启动：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\frontend"
npm.cmd run dev
```

## 2. tom 验收

### 2.1 登录

使用：

```text
username: tom
password: 123456
```

预期：

- 登录成功。
- 页面显示普通用户可用菜单。

### 2.2 打开 AI 页面并查询工单

在 AI 页面输入：

```text
查询工单
```

预期：

- 返回 tom 可见工单。
- 不应显示 alice 的 `验收-文件上传失败` 工单。

### 2.3 Network 检查

打开浏览器开发者工具 Network：

预期：

- 请求路径是 `/api/ai/chat` 或 Java `/ai/chat`。
- 不出现 `http://127.0.0.1:8001/agent/chat`。
- Header 中有 `Authorization: Bearer <token>`。

### 2.4 tom 不能修改状态

输入：

```text
把 1 号工单改成处理中
```

再点 Confirm。

预期：

- Java 返回无权限。
- 工单状态不变化。

## 3. staff 验收

### 3.1 登录

使用：

```text
username: staff
password: 123456
```

### 3.2 AI 创建工单

输入：

```text
帮我创建一个工单，标题是验收-前端AI创建，描述是用于验证前端确认流程，优先级高
```

预期：

- 页面返回确认提示。
- 此时数据库还没有新增该工单。

点击 `Confirm`。

预期：

- 页面返回已创建工单。
- 数据库 `ticket` 表新增 `验收-前端AI创建`。

再次点击 `Confirm`。

预期：

- 不重复创建。
- 页面提示当前没有待确认操作或等价提示。

### 3.3 AI 修改状态

输入：

```text
把 1 号工单改成处理中
```

预期：

- 页面返回确认提示。
- 点击 `Confirm` 后状态真实变化。
- 若工单已 `CLOSED`，再次改回 `OPEN` 或 `PROCESSING` 应失败。

### 3.4 取消操作

输入：

```text
帮我创建一个工单，标题是验收-前端取消，描述是取消后不应创建，优先级中
```

点击 `Cancel`。

预期：

- 页面返回已取消。
- 数据库没有新增 `验收-前端取消`。

## 4. 回复建议验收

### 4.1 生成回复建议

在右侧 Reply Suggestion 或 AI 能力入口输入一个可访问的工单 ID，点击生成。

预期页面展示：

- `suggestion`
- `confidence`
- `reason`
- `risk_flags`

如果 `risk_flags` 包含 `信息不足`、`需要人工确认`，前端应显示为风险标签，而不是页面报错。

### 4.2 信息不足

选择描述很少或历史回复不足的工单生成建议。

预期：

- AI 不编造日志、监控、修复结果。
- `risk_flags` 可包含 `信息不足`。

## 5. 新 AI 能力入口

AI 页面应有以下入口按钮：

- 总结工单
- 优先级建议
- 分类建议
- 相似工单
- SLA 风险

逐个点击后预期：

- 请求仍走 `/api/ai/chat`。
- 返回结构化 JSON。
- 页面能渲染主要字段。
- 无权限时不继续生成虚假结果。

## 6. conversationId 验收

同一个 AI 页面会话中：

1. 发送创建或修改请求。
2. 点击 `Confirm`。
3. 点击 `Cancel`。

预期：

- 请求 body 中 `conversationId` 不变。
- Confirm/Cancel 能找到同一条 pending_action。
- 刷新页面或新开会话可以生成新的 `conversationId`。

## 7. 401 清登录态

手工方式：

1. 登录后打开开发者工具 Application。
2. 删除或篡改本地 token。
3. 在 AI 页面发送请求。

预期：

- 前端收到 401 或 token 错误。
- 清除登录态。
- 提示重新登录。
- 不能继续展示受保护数据。

## 8. 数据库验证 SQL

检查 pending_action：

```sql
SELECT id, user_id, conversation_id, action_type, status, payload_json
FROM ai_pending_action
ORDER BY id DESC
LIMIT 20;
```

检查 AI 回复：

```sql
SELECT id, ticket_id, user_id, reply_type, content
FROM ticket_reply
WHERE reply_type = 'AI'
ORDER BY id DESC;
```

检查审计日志：

```sql
SELECT id, user_id, operation_type, business_type, business_id, content, created_at
FROM operation_log
ORDER BY id DESC
LIMIT 30;
```

## 9. 通过标准

- 前端不直连 Python。
- AI 查询可用。
- 写操作先 pending。
- Confirm 才执行真实写操作。
- Cancel 不执行。
- 重复 Confirm 不重复执行。
- 回复建议结构化字段展示完整。
- 401 后清登录态。
- tom / staff / admin 权限表现符合角色。

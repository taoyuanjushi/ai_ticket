# Task 1 Project Reading Report

## 1. 当前前端 TicketDetailPage 是否存在

存在：`frontend/src/pages/TicketDetailPage.tsx`。当前页面已经包含工单基础信息、回复列表、快捷回复、状态修改、负责人分配、操作日志和 `TicketAiAssistantPanel`。

## 2. 当前详情页调用了哪些接口

- `GET /tickets/{id}/detail`：加载工单、创建人和回复。
- `POST /tickets/{id}/replies`：普通回复。
- `PUT /tickets/{id}/status`：STAFF/ADMIN 修改状态。
- `PATCH /tickets/{id}/assignee`：STAFF/ADMIN 分配。
- `POST /ai/chat`：生成 AI 建议、摘要、分类等。
- `POST /tickets/{id}/ai-replies/pending`：保存 AI 回复建议前创建 pending_action。
- `POST /tickets/{id}/category/pending`：采纳 AI 分类前创建 pending_action。
- `GET /tickets/{id}/logs`：查看工单日志。

## 3. 当前回复列表字段有哪些

前端类型 `TicketReply` 当前字段：

- `id`
- `ticketId`
- `userId`
- `content`
- `replyType`
- `createdAt`
- `updatedAt`

缺口：没有 `authorName`、`authorRole`，页面只能显示 `#userId`。

## 4. 当前是否支持 USER 追加回复

支持。`TicketDetailPage` 调用 `api.replyTicket(ticketId, reply)`，Java `TicketReplyService.createReply` 会校验 USER 只能回复自己的工单。

## 5. 当前是否支持 STAFF 手动回复

支持。STAFF/ADMIN 回复会由后端根据当前角色保存为 `TicketReplyType.STAFF`，不是由前端传入。

## 6. 当前是否支持 STAFF 生成 AI 回复建议

支持。详情页右侧 `TicketAiAssistantPanel` 有“生成 AI 回复建议”按钮，通过 Java `/ai/chat` 转发到 Python `/agent/chat`。

## 7. 当前 AI 回复建议是否可编辑

不完整。当前 AI 建议可以展示并点击“保存 AI 回复”，但保存的是原始 `payload.suggestion`，没有给 STAFF 编辑 suggestion 的输入框。

## 8. 当前保存 AI 回复是否走 pending_action

支持。前端调用 `POST /tickets/{id}/ai-replies/pending`，Java `AiPendingActionService.createSaveAiReplyPending` 创建 `SAVE_AI_REPLY` pending_action。确认后才执行 `TicketReplyService.createAiReply`。

## 9. 当前普通 USER 是否可能看到未采纳 AI 建议

生产数据层面不会。未采纳 AI 建议只存在当前 STAFF 前端组件状态和 Java pending_action payload 中，不进入工单回复列表。USER 打开详情只会看到已经保存的回复。

前端层面 `TicketAiAssistantPanel` 当前对 USER 也显示 AI 分析按钮，但保存 AI 回复按钮按 `canSaveAiReply` 隐藏。为满足“USER 不能看到未采纳 AI 建议”，最小方案是只让 STAFF/ADMIN 看到详情页 AI 助手。

## 10. 当前 `/tickets/{id}/detail` 返回结构

Java 当前返回 `TicketDetailVO`：

- `ticket`
- `user`
- `replies`

其中 `replies` 是 `TicketReply` 实体列表，按 `createdAt` 升序；缺少回复作者姓名和角色。

## 11. 当前 `/tickets/{id}/replies` 行为

- `POST /tickets/{ticketId}/replies` 保存普通回复。
- USER 只能回复自己的工单。
- STAFF/ADMIN 可回复。
- 关闭工单不能继续回复。
- 回复类型由后端按登录角色决定。

## 12. 当前 `/tickets/{id}/ai-replies/pending` 行为

- 只有 STAFF/ADMIN 可以创建 `SAVE_AI_REPLY` pending_action。
- USER 调用会在 `PermissionUtil.requireStaffOrAdmin()` 返回 403。
- 创建 pending_action，不直接保存回复。
- pending_action 按 `userId + conversationId` 查询和确认。
- 过期时间为 10 分钟。
- 重复确认通过状态条件更新避免重复执行。

缺口：请求 DTO 当前只接收 `content`，没有保存 `confidence/reason/riskFlags` 到 payload。

## 13. 当前 Python AI 回复建议输出结构

当前 `ReplySuggestionData` 输出：

- `suggestion`
- `confidence`
- `reason`
- `risk_flags`

缺口：没有固定返回 `intent=GENERATE_REPLY_SUGGESTION` 和 `actionType=AI_REPLY_SUGGESTION`。

## 14. 当前权限校验是否满足 USER / STAFF / ADMIN 要求

大体满足：

- USER 详情读取由 `TicketService.checkTicketReadable` 限制本人。
- USER 回复由 `TicketReplyService.createReply` 限制本人。
- AI 回复 pending 由 `AiPendingActionService.validateActionCreationPermission` 限制 STAFF/ADMIN。
- 前端管理页按角色隐藏，后端仍有强制校验。

需人工确认：STAFF “只能查看分配给自己或待处理工单”当前后端实现比这个更宽，STAFF 可以读取非 USER 限制下的工单。这个权限收紧会影响既有链路，本任务不做大改。

## 15. 当前缺口列表

1. 回复列表缺少 `authorName` / `authorRole`。
2. AI 回复建议保存前不能编辑。
3. `ai-replies/pending` 未保存 `confidence/reason/riskFlags`。
4. Python 回复建议缺少固定 `intent/actionType`。
5. USER 详情页仍能看到 AI 助手的未采纳建议组件。

## 16. 最小修改方案

1. Java：在 `TicketReply` 增加非数据库字段 `authorName`、`authorRole`、`isAiGenerated`，由 `TicketService.getTicketDetail` 填充，不改表。
2. Java：扩展 `AiReplyPendingRequest`，接收可选 `confidence/reason/riskFlags`，写入 pending_action payload。
3. 前端：扩展 `TicketReply` 类型并展示作者姓名/角色。
4. 前端：在 AI 回复建议卡内加入可编辑 textarea，“保存 AI 回复”提交编辑后的内容。
5. 前端：详情页仅 STAFF/ADMIN 显示 `TicketAiAssistantPanel`。
6. Python：给 `ReplySuggestionData` 增加默认 `intent` 和 `actionType` 字段，并更新 prompt 输出格式。


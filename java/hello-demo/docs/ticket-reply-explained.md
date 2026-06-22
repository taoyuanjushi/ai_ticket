# TicketReply 工单回复模块核心概念说明

本文档解释当前项目中 TicketReply 工单回复模块的设计原因。适合配合以下代码一起阅读：

- `controller/TicketReplyController.java`
- `service/TicketReplyService.java`
- `dto/TicketReplyCreateDTO.java`
- `entity/TicketReply.java`
- `enums/TicketReplyType.java`
- `mapper/TicketReplyMapper.java`
- `resources/sql/ticket_reply.sql`

## 1. Ticket 和 TicketReply 为什么是一对多关系？

`Ticket` 表示一张工单，`TicketReply` 表示这张工单下面的一条回复。

真实业务中，一张工单通常不是只有一句话就结束，而是会有一个沟通过程：

```text
用户提交工单
客服第一次回复
用户补充截图
客服继续排查
AI 给出建议
客服确认解决
```

所以一张工单下面可能有多条回复。

对应关系就是：

```text
一个 Ticket  -> 多个 TicketReply
```

数据库上表现为：

```text
ticket.id = ticket_reply.ticket_id
```

也就是说，`ticket_reply.ticket_id` 指向它所属的工单。

## 2. ticket_reply 表里为什么要有 ticket_id？

`ticket_id` 用来说明这条回复属于哪一张工单。

例如：

| id | ticket_id | content |
| --- | --- | --- |
| 1 | 1 | 我已经尝试重置密码，但还是无法登录。 |
| 2 | 1 | 请确认账号是否已经注册。 |
| 3 | 2 | 页面加载慢的问题已经收到。 |

这里：

- `ticket_id = 1` 的回复属于工单 1；
- `ticket_id = 2` 的回复属于工单 2。

如果没有 `ticket_id`，后端就不知道一条回复应该显示在哪个工单详情页里。

当前项目中，新增回复时使用路径里的 `ticketId`：

```text
POST /tickets/{ticketId}/replies
```

然后 Service 中设置：

```java
reply.setTicketId(ticketId);
```

## 3. 为什么不能把所有回复都放在 ticket 表里？

不能把所有回复都放在 `ticket` 表里，主要因为回复是“多条数据”，而工单是“一条主数据”。

如果把回复都塞进 `ticket` 表，会出现几个问题：

- 一张工单有多少条回复不固定，表字段很难设计。
- 可能需要 `reply1`、`reply2`、`reply3` 这种字段，后续扩展很差。
- 回复内容会让 `ticket` 表越来越臃肿。
- 查询工单列表时，本来只需要标题、状态、优先级，却会带出大量回复内容。
- 单独查询、排序、统计回复会很困难。

更合理的设计是：

```text
ticket 表：保存工单主信息
ticket_reply 表：保存工单回复记录
```

这样表职责清晰：

- `ticket` 管工单本身；
- `ticket_reply` 管工单的沟通过程。

## 4. POST /tickets/{ticketId}/replies 这种路径表达了什么含义？

这个路径表达的是：

```text
在某一张工单下面新增一条回复
```

拆开看：

```text
/tickets                工单资源
/{ticketId}             指定某一张工单
/replies                这张工单下面的回复资源
POST                    新增
```

所以：

```text
POST /tickets/1/replies
```

意思是：给 id 为 1 的工单新增一条回复。

这种路径比下面这种更清晰：

```text
POST /replies
```

因为它直接表达了回复和工单的从属关系。

## 5. @PathVariable 在这个接口里负责什么？

`@PathVariable` 用来从 URL 路径中取值。

当前 Controller 中：

```java
@PostMapping
public Result<TicketReply> createReply(
        @PathVariable Long ticketId,
        @Valid @RequestBody TicketReplyCreateDTO dto) {
    ...
}
```

当请求是：

```text
POST /tickets/1/replies
```

Spring 会把路径里的 `1` 绑定到方法参数：

```java
Long ticketId = 1L;
```

也就是说，`@PathVariable` 在这里负责拿到“当前要给哪张工单新增回复”。

## 6. 新增回复前为什么要先判断工单是否存在？

因为回复必须属于一张真实存在的工单。

如果不判断工单是否存在，前端可以请求：

```text
POST /tickets/999999/replies
```

然后数据库里可能产生一条：

```text
ticket_id = 999999
```

但实际上根本没有 id 为 `999999` 的工单。

这种数据叫“孤儿数据”，后续会带来问题：

- 查询工单详情时永远查不到这条回复；
- 数据库里出现无效记录；
- 统计回复数量时结果不可靠；
- 后续排查问题很困难。

所以当前项目在 `TicketReplyService` 中先调用：

```java
Ticket ticket = ticketMapper.selectById(ticketId);
```

如果查不到，就抛出：

```java
throw new BusinessException(404, "工单不存在");
```

## 7. 为什么 CLOSED 工单不允许继续回复？

`CLOSED` 表示工单已经关闭，流程已经结束。

如果关闭后仍然允许回复，会造成几个问题：

- 已关闭的工单又出现新的沟通内容，业务状态和实际内容不一致。
- 客服或用户可能以为问题还在处理中。
- 报表统计会混乱，比如关闭工单又持续产生新回复。
- 后续如果接入通知系统，关闭工单还产生回复会让通知逻辑变复杂。

所以当前项目规定：

```java
if (TicketStatus.CLOSED.name().equals(ticket.getStatus())) {
    throw new BusinessException(400, "已关闭工单不允许继续回复");
}
```

如果确实需要继续沟通，更合理的做法通常是：

- 新建一张工单；
- 或者未来设计“重新打开工单”的明确流程。

## 8. TicketReplyService 为什么需要同时调用 TicketMapper 和 TicketReplyMapper？

因为新增回复涉及两类数据：

1. 工单本身；
2. 工单回复。

`TicketMapper` 用来检查工单：

- 工单是否存在；
- 工单是否已经 `CLOSED`。

`TicketReplyMapper` 用来操作回复：

- 新增回复；
- 查询回复列表。

所以 Service 需要同时注入：

```java
private final TicketReplyMapper ticketReplyMapper;
private final TicketMapper ticketMapper;
```

这样做符合分层职责：

- Mapper 只负责数据库访问；
- Service 负责把多个 Mapper 组合起来完成业务流程。

## 9. LambdaQueryWrapper 在查询回复列表时有什么作用？

`LambdaQueryWrapper` 用来拼接查询条件。

当前查询某个工单下的回复列表时，代码是：

```java
LambdaQueryWrapper<TicketReply> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(TicketReply::getTicketId, ticketId);
wrapper.orderByAsc(TicketReply::getCreatedAt);

return ticketReplyMapper.selectList(wrapper);
```

它表达的 SQL 含义大致是：

```sql
SELECT *
FROM ticket_reply
WHERE ticket_id = ?
ORDER BY created_at ASC
```

这里有两个关键点：

- `eq(TicketReply::getTicketId, ticketId)`：只查当前工单的回复。
- `orderByAsc(TicketReply::getCreatedAt)`：按创建时间从早到晚排序，形成正常的沟通时间线。

使用 `TicketReply::getTicketId` 这种写法，比手写 `"ticket_id"` 字符串更安全。

## 10. replyType 为什么要限制为 USER、STAFF、AI？

`replyType` 用来说明回复是谁或什么来源产生的。

当前项目限制为：

| replyType | 含义 |
| --- | --- |
| `USER` | 普通用户回复 |
| `STAFF` | 客服或管理员回复 |
| `AI` | AI 回复建议 |

限制类型有几个好处：

- 前端可以根据类型展示不同样式，比如用户回复靠右、客服回复靠左。
- 后续可以统计不同来源的回复数量。
- 可以区分人工回复和 AI 建议。
- 避免数据库出现 `XXX`、`abc`、`管理员` 这种不统一的值。

当前项目用 `TicketReplyType` 枚举统一判断：

```java
if (!TicketReplyType.isValid(replyType)) {
    throw new BusinessException(400, "回复类型不合法");
}
```

## 11. content 为空时后端应该怎么返回友好提示？

`content` 是回复内容，不能为空。

当前项目在 `TicketReplyCreateDTO` 中使用：

```java
@NotBlank(message = "content不能为空")
private String content;
```

如果前端传：

```json
{
  "userId": 1,
  "content": "",
  "replyType": "USER"
}
```

Spring Validation 会拦截这个请求，抛出参数校验异常。

然后 `GlobalExceptionHandler` 会统一返回：

```json
{
  "code": 400,
  "message": "content不能为空",
  "data": null
}
```

这样前端可以直接把 `message` 展示给用户，不需要解析复杂异常。

## 12. 这个模块以后怎么接入 AI 回复建议？

当前项目已经预留了 `replyType = AI`。

以后接入 AI 回复建议时，可以按这个方向扩展：

1. 用户或客服在工单中新增回复。
2. 后端把工单标题、内容、历史回复发送给 AI 服务。
3. AI 返回一段建议回复。
4. 后端把 AI 建议保存到 `ticket_reply` 表。
5. 保存时设置：

```java
reply.setReplyType(TicketReplyType.AI.name());
```

这样 AI 回复和人工回复可以共用同一张 `ticket_reply` 表。

未来可能新增接口：

```text
POST /tickets/{ticketId}/replies/ai-suggestion
```

或者在客服回复页面提供一个“生成 AI 建议”的按钮。

但要注意：

- AI 建议不应该绕过当前的工单存在校验。
- CLOSED 工单是否允许生成 AI 建议，需要单独定义业务规则。
- AI 生成的内容最好先给客服确认，再作为正式回复发出。
- 如果只是建议，可以先保存为 `replyType = AI`，并在前端标注“AI 建议”。

## 小结

TicketReply 模块的核心调用链是：

```text
前端请求 POST /tickets/{ticketId}/replies
        ↓
TicketReplyController 接收 ticketId 和请求体
        ↓
TicketReplyCreateDTO 校验 userId/content/replyType
        ↓
TicketReplyService 判断工单是否存在、是否 CLOSED、replyType 是否合法
        ↓
TicketReplyMapper 写入 ticket_reply 表
        ↓
Controller 返回 Result<TicketReply>
```

查询回复列表的调用链是：

```text
前端请求 GET /tickets/{ticketId}/replies
        ↓
TicketReplyController 接收 ticketId
        ↓
TicketReplyService 判断工单是否存在
        ↓
TicketReplyMapper 按 ticketId 查询回复并按 createdAt 升序排序
        ↓
Controller 返回 Result<List<TicketReply>>
```

这个模块的重点是：

- `Ticket` 保存工单主信息；
- `TicketReply` 保存工单沟通过程；
- `ticket_id` 建立一对多关系；
- DTO 控制接口入参；
- enum 控制回复类型；
- Service 负责业务规则；
- Mapper 负责数据库访问。

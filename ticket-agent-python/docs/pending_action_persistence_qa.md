# pending_action 持久化学习复盘问答

本文用初学者能理解的方式，解释这次为什么要把 `pending_action` 从 Python 内存状态升级为 Java 托管的持久化状态，以及 Redis、数据库、幂等、用户和会话隔离分别解决什么问题。

当前阶段的核心变化是：

```text
以前：
Python /agent/chat
↓
pending_action 保存在 Python 内存 dict
↓
用户确认
↓
Python 再调用 Java 写接口

现在：
Python /agent/chat
↓
Java /ai/pending-actions 保存 pending_action
↓
pending_action 落到 ai_pending_action 表
↓
用户确认
↓
Python 调 Java confirm 接口
↓
Java 校验用户和状态后，走 TicketService / TicketReplyService 执行业务动作
```

对应的数据库表是：

```sql
CREATE TABLE ai_pending_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    cancelled_at DATETIME NULL
);
```

其中 `payload_json` 只保存业务参数，例如：

```json
{
  "ticketId": 1,
  "status": "PROCESSING"
}
```

不能保存 `token` 或 `Authorization`。

## 1. pending_action 为什么不能只存在 Python 内存？

Python 内存可以理解为“程序运行时临时放东西的地方”。

比如第一版里可以这样保存：

```python
store = {
    "user:7:conversation:chat-1": PendingAction(...)
}
```

这在本地学习阶段很好用，因为写起来简单，测试也方便。

但它不适合真实系统，原因有几个。

第一，服务重启后会丢。

```text
用户发起：把 1 号工单改成处理中
Python 内存里保存 pending_action
Python 服务重启
内存清空
用户再说：确认
系统找不到刚才那个 pending_action
```

第二，多进程或多机器时不共享。

真实部署时，Python AI 服务可能有多个 worker：

```text
第 1 次请求进到 Python worker A
pending_action 保存在 worker A 的内存

第 2 次“确认”请求进到 Python worker B
worker B 的内存里没有这个 pending_action
```

用户明明已经发起过操作，但系统却说“没有待确认的操作”。

第三，不方便审计。

企业系统通常要知道：

```text
谁发起了待确认动作？
什么时候发起？
是什么动作？
什么时候确认？
什么时候取消？
最终有没有执行？
```

内存里的状态不适合长期追踪，也不适合给管理员查询。

第四，权限边界不清晰。

Java 后端才是工单系统的业务中心：

```text
Java 管用户身份
Java 管权限
Java 管工单状态流转
Java 管数据库
Java 管操作日志
```

如果 pending_action 只存在 Python 内存，确认态就离真正的业务规则太远了。

所以这次改成：

```text
Python 负责理解用户意图
Java 负责保存 pending_action 和最终执行业务动作
数据库负责可靠保存状态
```

这样更接近生产系统。

## 2. Redis 和数据库保存状态有什么区别？

Redis 和数据库都可以保存 `pending_action`，但它们适合的场景不一样。

可以用一个简单类比：

```text
Redis 像临时便签，读写很快，适合短期状态。
数据库像正式档案，保存更完整，适合审计和长期追踪。
```

如果用 Redis，可以这样设计 key：

```text
ai:pending:{userId}:{conversationId}
```

value 可能是：

```json
{
  "actionType": "UPDATE_TICKET_STATUS",
  "payload": {
    "ticketId": 1,
    "status": "PROCESSING"
  },
  "createdAt": "2026-06-17T10:00:00",
  "expireAt": "2026-06-17T10:10:00"
}
```

Redis 的优点是：

- 读写快。
- 天然适合设置过期时间。
- 适合“10 分钟内确认，过期就不要了”的临时状态。
- 实现比数据库表更轻。

Redis 的缺点是：

- 更像缓存，不天然适合审计。
- 数据结构通常没有数据库表那么清楚。
- 如果没有开启持久化，极端情况下可能丢数据。
- 管理员后续想查“谁确认过什么”，不如数据库方便。

数据库表的优点是：

- 结构清楚，有字段、有状态、有时间。
- 方便审计和排查问题。
- 可以记录 `PENDING / CONFIRMED / CANCELLED / EXPIRED` 全生命周期。
- 可以和 Java 的用户、权限、工单业务放在同一个系统里。
- 后续可以做后台管理页面或操作日志关联。

数据库表的缺点是：

- 实现成本比 Redis 高。
- 要设计表结构、实体、Mapper、Service、接口。
- 需要处理状态更新和并发确认。

当前项目最终选择数据库表，也就是 Java 托管：

```text
ai_pending_action 表
```

原因是这个工单系统已经有 Java 后端、MySQL、JWT、权限、工单业务和操作日志。把确认态放到 Java 表里，边界更清晰，也更方便审计。

可以简单记：

```text
只是短期临时状态：Redis 可以。
需要权限、审计、业务一致性：数据库更合适。
```

## 3. 什么是幂等？为什么重复确认不能重复创建？

幂等是一个后端开发里很重要的概念。

初学时可以这样理解：

```text
同一个操作执行一次和执行多次，最终结果应该一样。
```

比如“查询工单”天然比较接近幂等：

```text
查一次：返回工单列表
再查一次：还是返回工单列表
不会多创建数据
```

但“创建工单”不是天然幂等：

```text
确认一次：创建 1 条工单
再确认一次：又创建 1 条工单
```

如果用户因为网络卡顿、前端重复点击、浏览器重发请求，导致“确认”发了两次，就可能出现重复创建：

```text
第 1 次确认：创建工单 #10
第 2 次确认：又创建工单 #11
```

这就是业务错误。

所以确认操作必须做成幂等。

这次 Java 托管 pending_action 的做法是：

```text
只有 status = PENDING 的动作才能确认
确认成功后状态改成 CONFIRMED
再次确认时已经不是 PENDING
所以不会再次执行业务动作
```

可以把它想成一张票：

```text
PENDING：票还没用，可以入场
CONFIRMED：票已经用过，不能再用
CANCELLED：票作废，不能再用
EXPIRED：票过期，不能再用
```

Java 确认时会做类似这样的条件更新：

```text
UPDATE ai_pending_action
SET status = 'CONFIRMED', confirmed_at = now()
WHERE id = ? AND user_id = 当前登录用户 AND status = 'PENDING'
```

关键点是：

```text
status 必须还是 PENDING，才能确认成功。
```

如果已经确认过，状态就是 `CONFIRMED`，这条更新就不会成功，业务动作也不会再次执行。

这对创建工单尤其重要：

```text
重复确认不能重复创建工单
重复确认不能重复保存 AI 回复
重复确认不能重复修改状态
```

一句话总结：

```text
幂等就是防止同一个确认动作被重复消费。
```

## 4. 为什么 pending_action 必须按 userId + conversationId 隔离？

因为“确认”这两个字本身没有业务参数。

用户说：

```text
确认
```

系统必须知道他是在确认哪一个待执行动作。

如果只按 `userId` 隔离，会有一个问题：同一个用户可能同时打开多个聊天窗口。

比如：

```text
用户 7 的 chat-1：
把 1 号工单改成处理中

用户 7 的 chat-2：
创建一个登录失败工单
```

如果只按 `userId=7` 保存，就可能互相覆盖。

这时用户在 `chat-1` 里说“确认”，系统可能执行了 `chat-2` 的创建工单动作。

所以还需要 `conversationId`。

更安全的隔离方式是：

```text
userId + conversationId
```

也就是：

```text
同一个用户的不同会话互不影响
不同用户的同一个 conversationId 也互不影响
```

数据库表里对应字段是：

```text
user_id
conversation_id
status
```

查询当前待确认动作时，会按当前登录用户和当前会话查：

```text
user_id = 当前登录用户
conversation_id = 当前会话
status = PENDING
```

这样能解决两个问题。

第一，不同用户隔离：

```text
用户 A 不能确认用户 B 的 pending_action
```

因为 Java 的 `user_id` 来自 JWT 登录上下文，不相信前端或 Python 自己传的用户 ID。

第二，不同会话隔离：

```text
同一个用户 chat-1 的确认
不会执行 chat-2 的 pending_action
```

这对 AI 对话尤其重要，因为用户可能同时开多个窗口处理不同工单。

可以记成一句话：

```text
userId 解决“是谁”的问题，conversationId 解决“是哪一次对话”的问题。
```

两者一起用，才能准确找到“这句话确认的是哪一个 pending_action”。

## 总结

这次改造的核心目标是：

```text
让 AI 写操作的确认态从临时内存状态
升级为 Java 后端可管理、可审计、可幂等的业务状态
```

几个关键结论：

- `pending_action` 不能只放 Python 内存，因为会丢失、不共享、难审计。
- Redis 适合短期临时状态，数据库更适合权限、审计和业务一致性。
- 幂等是为了防止重复确认导致重复创建、重复保存或重复修改。
- `pending_action` 必须按 `userId + conversationId` 隔离，避免用户和会话之间互相串单。
- `payload_json` 只保存业务参数，不能保存 token。

最终架构可以理解为：

```text
Python Agent：理解用户意图，发起待确认动作
Java 后端：保存 pending_action，校验用户，确认后执行业务动作
MySQL：保存 pending_action 全生命周期状态
```

这让 AI 工单系统从“本地能跑通”更进一步，变成“更接近真实生产系统”的设计。

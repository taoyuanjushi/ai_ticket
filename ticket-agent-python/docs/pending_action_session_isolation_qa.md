# pending_action 确认态隔离学习复盘问答

本文用初学者能理解的方式，解释当前 Python AI 服务里为什么需要 `pending_action`，以及为什么第三阶段要把它改成按 `user_id` 或 `conversation_id` 隔离保存。

当前项目里的核心流程是：

```text
用户发送消息
↓
Python /agent/chat
↓
AgentToolService 判断是查询、创建、修改、确认还是取消
↓
查询操作直接执行
↓
写操作先保存 pending_action，等待用户确认
↓
用户回复“确认”后，才真正调用 Java API
```

## 1. 什么是 pending_action？

`pending_action` 可以理解为“等待用户确认的操作草稿”。

用户说：

```text
把 3 号工单改成处理中
```

Agent 不应该马上修改工单，而是先整理出一个待确认动作：

```json
{
  "tool_name": "update_ticket_status",
  "args": {
    "ticket_id": 3,
    "status": "PROCESSING"
  },
  "summary": "将 3 号工单状态修改为 PROCESSING",
  "action_type": "write"
}
```

这个对象就是 `pending_action`。

它表示：

```text
我已经理解了你想做什么，但还没有真正执行。
等你回复“确认”后，我再执行。
```

## 2. 为什么写操作不能直接执行？

因为写操作会改变真实数据。

例如：

```text
create_ticket：会创建一条新工单
update_ticket_status：会修改已有工单状态
```

如果 Agent 理解错了用户的话，或者用户说错了工单 ID，直接执行就可能造成错误数据。

比如用户本来想改 3 号工单，却说成了 2 号工单。如果 Agent 直接执行，2 号工单就被错误修改了。

所以写操作需要 Human-in-the-loop，也就是“人在回路中确认”：

```text
用户提出写操作
↓
Agent 整理参数
↓
Agent 返回确认提示
↓
用户确认
↓
Agent 才真正执行
```

这样可以降低误操作风险。

## 3. 为什么 pending_action 不能用一个全局变量保存？

一个全局变量只有一个位置。

如果所有用户都共享这个变量，就会发生覆盖：

```text
用户 A 发起：把 1 号工单改成处理中
全局 pending_action = A 的修改动作

用户 B 发起：创建一个登录失败工单
全局 pending_action = B 的创建动作
```

这时用户 A 再回复“确认”，系统看到的已经不是 A 的动作，而是 B 的动作。

结果可能变成：

```text
用户 A 的“确认”
执行了用户 B 的“创建工单”
```

这就是为什么不能只用一个全局变量保存 `pending_action`。

## 4. user_id 和 conversation_id 分别解决什么问题？

`user_id` 用来区分不同用户。

例如：

```text
user:A
user:B
```

这样用户 A 和用户 B 的待确认动作不会混在一起。

`conversation_id` 用来区分同一个用户的不同会话。

例如同一个用户同时打开两个聊天窗口：

```text
conversation:chat-001
conversation:chat-002
```

如果只用 `user_id`，这两个窗口仍然会共用同一个待确认动作。使用 `conversation_id` 后，每个聊天窗口都有自己的确认态。

当前项目里的规则是：

```text
优先使用 conversation_id
如果没有 conversation_id，再使用 user_id
如果两个都没有，本地测试时使用 default
```

## 5. 多用户同时使用 Agent 时会出现什么状态覆盖问题？

假设只有一个全局 `pending_action`：

```text
第 1 步：用户 A 说“把 1 号工单改成处理中”
pending_action = A 的状态修改

第 2 步：用户 B 说“创建一个登录失败工单”
pending_action = B 的创建工单

第 3 步：用户 A 说“确认”
系统执行 pending_action
```

问题是，第 3 步执行的是 B 的动作，不是 A 的动作。

这会带来两个严重问题：

- 用户 A 可能执行了自己根本没有发起的操作。
- 用户 B 的操作可能被用户 A 误确认。

所以多用户场景下，`pending_action` 必须按用户或会话隔离。

## 6. 为什么“确认”这句话必须知道属于哪个会话？

“确认”本身没有业务信息。

它不像下面这句话一样包含工单 ID 和目标状态：

```text
把 3 号工单改成处理中
```

“确认”只有一个意思：

```text
确认刚才那个待执行操作
```

问题在于，系统必须知道“刚才那个”是哪一个。

如果没有 `user_id` 或 `conversation_id`，系统就无法判断：

```text
这是用户 A 的确认？
还是用户 B 的确认？
是 chat-001 的确认？
还是 chat-002 的确认？
```

所以确认消息必须带着当前会话身份，才能找到正确的 `pending_action`。

## 7. pending_action 按会话保存后，数据结构应该怎么设计？

不能再设计成一个单独变量：

```python
pending_action = None
```

应该设计成一个字典：

```python
store = {
    "user:A": PendingAction(...),
    "user:B": PendingAction(...),
    "conversation:chat-001": PendingAction(...),
    "conversation:chat-002": PendingAction(...)
}
```

当前项目里就是类似这样的结构：

```text
session_key -> PendingAction
```

`session_key` 的生成规则是：

```text
conversation_id 存在：conversation:{conversation_id}
user_id 存在：user:{user_id}
都不存在：default
```

这样每个用户或会话都有自己的待确认动作。

## 8. 为什么 create_ticket / update_ticket_status 这种写操作必须绑定当前用户或会话？

因为它们会改变 Java 后端里的真实工单数据。

`create_ticket` 会创建新工单：

```text
POST /tickets
```

`update_ticket_status` 会修改工单状态：

```text
PUT /tickets/{id}/status
```

这些操作必须知道是谁发起的、属于哪个会话，否则就无法安全确认。

比如：

```text
用户 A 发起修改 1 号工单
用户 B 发起创建工单
```

如果写操作不绑定用户或会话，后续“确认”就可能确认错对象。

所以写操作保存为 `pending_action` 时，必须保存到当前 `session_key` 下。

## 9. 如果用户 A 发起确认，为什么不能执行用户 B 的 pending_action？

因为用户 A 没有确认用户 B 的操作。

从业务安全角度看：

```text
谁发起的写操作
谁所在的会话确认
才应该执行这个写操作
```

如果 A 的确认执行了 B 的 `pending_action`，会造成权限和责任混乱：

- A 可能不知道自己执行了什么。
- B 可能没有真正确认自己的操作。
- 系统日志也很难解释到底是谁批准了这个动作。

所以确认时必须这样查：

```text
拿当前请求的 user_id / conversation_id
↓
生成 session_key
↓
只读取这个 session_key 下的 pending_action
```

如果当前会话没有待确认动作，就应该返回：

```text
当前会话没有待确认的操作。
```

## 10. pending_action 什么时候应该被清除？

`pending_action` 应该在以下几种情况被清除：

1. 用户确认后，不管执行成功还是失败，都应该清除当前会话的 `pending_action`。
2. 用户取消后，应该清除当前会话的 `pending_action`。
3. `pending_action` 超过有效期后，读取时应该清除。
4. 测试用例开始或结束时，应该清空内存状态，避免测试之间互相影响。

简单说：

```text
pending_action 只应该存在于“等待确认”的时间段。
一旦不再等待确认，就应该被清理。
```

## 11. 确认后为什么要删除 pending_action？

因为确认后，这个待执行动作已经被消费了。

如果不删除，就会出现重复执行风险：

```text
用户说：确认
系统修改了 1 号工单

用户又说：确认
如果 pending_action 没删，系统可能再次执行同一个动作
```

对于创建工单尤其危险：

```text
第一次确认：创建一个登录失败工单
第二次确认：又创建一个一模一样的工单
```

所以确认后必须删除 `pending_action`。

## 12. 取消后为什么也要删除 pending_action？

因为用户已经明确表示不执行这个操作。

如果取消后不删除，就会发生这种问题：

```text
用户说：取消
系统回复：已取消

用户后来又说：确认
旧的 pending_action 还在
系统又把之前取消的动作执行了
```

这和用户的意愿相反。

所以取消后也必须删除当前会话的 `pending_action`。

## 13. pending_action 过期机制有什么用？

过期机制用于防止旧的待确认动作长期留在内存里。

假设用户今天上午发起一个修改操作，但没有确认。下午他回来随手说一句“确认”，如果旧 `pending_action` 还在，系统可能执行一个用户早就忘记的操作。

过期机制可以避免这种情况。

当前项目里可以理解为：

```text
pending_action 创建后有一个 created_at
PendingActionStore 读取时检查是否超过 expire_seconds
如果超过，就删除并返回 None
```

这样过期动作不会被继续确认。

## 14. 第一版用内存保存 pending_action 有什么问题？

内存保存适合学习和本地测试，但不适合生产。

主要问题有：

- 服务重启后，内存里的 `pending_action` 会全部丢失。
- 多 worker 部署时，不同进程之间的内存不共享。
- 多台服务器部署时，每台机器都有自己的内存，状态不一致。
- 内存状态不方便审计，也不方便排查谁在什么时候发起了什么确认。
- 如果把 `auth_token` 暂存在内存里，还需要考虑敏感信息安全。

所以第一版用内存，是为了先跑通流程：

```text
先理解确认机制
再考虑生产化持久化
```

## 15. 后续为什么应该把 pending_action 持久化到 Java 或数据库？

因为生产系统需要更可靠、更可追踪的确认状态。

持久化后可以做到：

- 服务重启后，待确认动作仍然存在。
- 多个 Python worker 可以共享同一份确认状态。
- 多台服务器部署时状态一致。
- 可以记录创建时间、确认人、取消人、过期时间。
- 可以配合 Java 的用户权限和业务规则做更严格校验。
- 可以审计谁确认了哪个操作。

如果持久化到 Java，架构边界也更清晰：

```text
Python Agent 负责理解用户意图和发起确认流程
Java 后端负责保存待确认动作、权限校验、最终执行业务写入
数据库负责可靠保存状态
```

这更接近真实企业系统的设计。

## 总结

`pending_action` 是写操作的“待确认草稿”。它存在的目的不是多此一举，而是让 AI Agent 在执行真实业务写入前，先让用户确认一次。

第三阶段最重要的变化是：

```text
以前：所有人共享一个 pending_action
现在：每个 user_id 或 conversation_id 有自己的 pending_action
```

这样可以避免：

- 用户 A 的确认执行用户 B 的操作。
- 一个聊天窗口覆盖另一个聊天窗口的待确认动作。
- 旧的待确认动作被误执行。

当前内存实现适合学习。后续如果要生产化，应该把确认态持久化到 Java 或数据库中。

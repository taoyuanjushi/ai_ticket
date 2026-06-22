# 第四阶段学习复盘：确认 pending_action 时为什么必须重新鉴权

本文用于复盘第四阶段改造：用户确认 `pending_action` 时，系统不能依赖创建时的旧 token，而必须使用当前请求里携带的有效 `Authorization`。

可以先记住一个简单类比：

```text
pending_action = 一张待签字的操作申请单
JWT token = 用户当前手里的临时门禁卡
确认操作 = 用户拿着当前门禁卡，回来给申请单签字
```

申请单里可以写“我要创建什么工单、我要把哪个工单改成什么状态”，但不能把用户的门禁卡也夹在申请单里保存起来。

## 1. token 为什么不能持久化保存？

token 可以理解为“临时登录凭证”。谁拿到 token，谁就可能冒充这个用户去访问系统。

如果把 token 保存到数据库、Redis 或 `pending_action.payload` 里，会带来几个风险：

- 数据库泄露时，攻击者可以直接拿 token 冒充用户。
- token 本来应该过期，但系统如果保存旧 token，可能误用过期凭证。
- 日志、备份、调试工具可能把 token 扩散到更多地方。
- pending_action 本来只是业务状态，不应该变成登录凭证仓库。

所以 pending_action 里应该只保存业务参数，例如：

```json
{
  "title": "登录失败",
  "content": "用户输入正确密码后仍提示错误",
  "priority": "HIGH"
}
```

不应该保存：

```json
{
  "title": "登录失败",
  "auth_token": "Bearer xxxxx"
}
```

本阶段的原则是：token 只跟随当前 HTTP 请求走，不进入 pending_action 持久化状态。

## 2. 创建 pending_action 时有权限，为什么确认时还要重新鉴权？

因为“创建 pending_action”和“确认执行”是两个不同时间点的动作。

用户第一次说“帮我创建工单”时，系统只是保存一个待确认动作，并没有真正写数据库。过了一会儿，用户再说“确认”时，情况可能已经变了：

- 用户已经退出登录。
- token 已经过期。
- token 被篡改。
- 用户权限被管理员调整了。
- 另一个人拿到了 conversationId，试图替别人确认。

如果确认时不重新鉴权，系统就等于在说：“只要你以前来过，现在不用证明身份也能执行写操作。” 这对写操作很危险。

正确做法是：

```text
创建 pending_action：检查当前用户身份，保存业务参数
确认 pending_action：再次检查当前 token，重新确认当前用户是谁，再执行写操作
```

也就是说，确认不是简单地“执行以前存下来的东西”，而是“当前用户重新通过验证后，执行属于自己的待确认动作”。

## 3. 认证和授权有什么区别？

认证和授权很容易混，但它们不是一回事。

认证回答的是：

```text
你是谁？
```

例如 JWT 校验通过后，系统知道当前用户是 `userId=7`，角色是 `STAFF`。

授权回答的是：

```text
你能做什么？
```

例如：

- 普通用户可以创建自己的工单。
- 普通用户不能修改工单状态。
- STAFF 可以处理工单。
- ADMIN 可以查看操作日志。

在本阶段确认 pending_action 时，两件事都要做：

```text
先认证：当前请求里的 token 是否有效？当前用户是谁？
再授权：这个 pending_action 是否属于当前用户？当前用户是否有权限执行里面的业务动作？
```

只认证不授权，会出现“用户是真的，但他执行了不属于自己的操作”。

只授权不认证，会出现“系统不知道你是谁，却允许你操作”。

## 4. 什么是重放攻击？

重放攻击可以理解为：攻击者把别人曾经发过的一次有效请求复制下来，之后再发一遍，试图让系统重复执行。

举个例子：

```text
用户第一次确认：创建工单 A
攻击者复制这次确认请求
攻击者再次发送：又创建一张一样的工单 A
```

如果系统没有防护，就可能重复创建、重复修改、重复扣款，或者重复保存 AI 回复。

pending_action 场景下，防重放的关键是：

- pending_action 只能从 `PENDING` 变成 `CONFIRMED` 一次。
- 确认成功后不能再次执行。
- 取消后也不能再执行。
- 确认时用 `id + userId + status=PENDING` 这样的条件更新，保证只有还处于待确认状态的动作能被确认。

简单说：

```text
第一次确认：PENDING -> CONFIRMED，执行业务
第二次确认：已经不是 PENDING，不再执行业务
```

这就是幂等和防重复执行的核心思想。

## 5. 如何防止用户确认别人的 pending_action？

核心规则是：pending_action 必须绑定 owner，也就是创建它的用户。

保存 pending_action 时，不能相信前端或 Python 传来的 `userId`，而要由 Java 后端从当前登录上下文里取：

```text
userId = 当前 JWT 解析出的登录用户 ID
```

确认时也一样：

```text
currentUserId = 当前请求 JWT 解析出的用户 ID
pendingAction.userId 必须等于 currentUserId
```

如果不相等，说明当前用户正在尝试确认别人的待确认动作，必须拒绝。

本阶段的确认链路可以这样理解：

```text
前端携带当前 Authorization
-> Java /ai/chat 读取当前 Authorization
-> Java 转发给 Python /agent/chat
-> Python 确认时用当前 auth_token 调 Java confirm
-> Java 重新解析 token 得到 currentUserId
-> Java 校验 pending_action.user_id == currentUserId
-> 校验通过后才执行业务写操作
```

这样即使用户 B 知道了用户 A 的 `conversationId`，也不能确认用户 A 的 pending_action。

## 6. 为什么日志中不能打印完整 JWT？

日志不是保险箱。日志通常会被很多系统读取、采集、备份和检索，例如：

- 控制台日志
- 文件日志
- 日志平台
- 报警系统
- 排障截图
- CI/CD 输出

如果完整 JWT 出现在日志里，看到日志的人就可能拿它冒充用户。

所以日志里不能打印完整 token。最多只能打印脱敏后的信息，例如：

```text
Authorization: Bearer eyJhbGci...<masked>
```

或者更推荐只记录和排查有关的非敏感信息：

```text
userId=7, conversationId=chat-123, actionType=CREATE_TICKET
```

本阶段的思路是：排查问题时记录“谁、在哪个会话、做了什么动作”，而不是记录“这个人的登录凭证是什么”。

## 最后记忆

可以用这几句话记住第四阶段的安全边界：

```text
pending_action 保存业务参数，不保存 token。
确认时必须拿当前请求的 token 重新鉴权。
认证解决“你是谁”，授权解决“你能不能做”。
重复确认不能重复执行，避免重放攻击。
pending_action 必须校验 owner，不能确认别人的动作。
日志不能打印完整 JWT，因为日志也可能泄露。
```


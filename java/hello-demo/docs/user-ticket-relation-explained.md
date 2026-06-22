# User、Ticket、TicketReply 关联关系说明

本文说明当前项目中 `user`、`ticket`、`ticket_reply` 三张表的关系，以及用户和工单关联功能背后的设计原因。

## 1. user、ticket、ticket_reply 三张表之间是什么关系？

`user` 是用户表，保存系统用户信息。

`ticket` 是工单表，保存用户提交的工单信息。

`ticket_reply` 是工单回复表，保存某个工单下的沟通记录。

它们的核心关系是：

```text
user.id -> ticket.user_id
ticket.id -> ticket_reply.ticket_id
user.id -> ticket_reply.user_id
```

也就是说：

- 一个用户可以创建多个工单，所以 `user` 和 `ticket` 是一对多关系。
- 一个工单可以有多条回复，所以 `ticket` 和 `ticket_reply` 是一对多关系。
- 一个用户可以发表多条回复，所以 `user` 和 `ticket_reply` 也是一对多关系。

## 2. ticket.user_id 的作用是什么？

`ticket.user_id` 用来记录这张工单是谁创建的。

有了这个字段，后端才能知道：

- 某个工单属于哪个用户。
- 查询用户工单列表时，应该返回哪些工单。
- 查询工单详情时，应该关联哪个提交人信息。
- 后续接入登录和权限后，当前登录用户是否有权限查看或操作这张工单。

如果没有 `ticket.user_id`，工单就只是一条孤立记录，无法和提交人建立明确关系。

## 3. ticket_reply.ticket_id 的作用是什么？

`ticket_reply.ticket_id` 用来记录这条回复属于哪一张工单。

例如调用 `GET /tickets/{ticketId}/replies` 时，后端会根据 `ticketId` 查询这个工单下的所有回复。查询工单详情时，`TicketService` 也会根据工单 id 查询对应回复列表，并组装到 `TicketDetailVO` 中。

如果没有 `ticket_reply.ticket_id`，回复就无法归属到具体工单，也就无法展示某个工单的完整沟通过程。

## 4. ticket_reply.user_id 的作用是什么？

`ticket_reply.user_id` 用来记录这条回复是谁发表的。

它和 `ticket_reply.ticket_id` 的关注点不同：

- `ticket_reply.ticket_id` 说明回复属于哪张工单。
- `ticket_reply.user_id` 说明回复是哪个用户发出的。

当前项目中的 `GET /users/{userId}/ticket-replies` 就是根据 `ticket_reply.user_id` 查询某个用户发表过的回复记录。

后续做权限、审计、客服处理记录、AI 回复建议时，也需要知道每条回复的来源。

## 5. 创建工单时为什么必须检查 userId 是否存在？

创建工单时，`userId` 表示提交工单的用户。后端必须先检查这个用户是否存在，否则会产生无效数据。

如果不检查，可能出现这些问题：

- `ticket.user_id` 指向一个不存在的用户。
- 查询工单详情时，无法查到提交人信息。
- 后续权限判断无法确认工单归属。
- 数据库中出现孤儿数据，业务含义不完整。

当前项目在 `TicketService.createTicket` 中调用 `validateTicketUser(ticket.getUserId())`，如果 `userId` 为空会返回 `userId不能为空`，如果用户不存在会返回 `用户不存在`。

## 6. GET /users/{userId}/tickets 表达了什么含义？

`GET /users/{userId}/tickets` 表示查询某个用户提交过的工单列表。

路径中的层级表达了资源归属关系：

```text
/users/{userId}/tickets
```

含义是：在某个用户下面，查询这个用户关联的工单。

当前项目中，这个接口由 `UserController` 接收请求，再调用 `UserService.getTicketsByUserId(userId)`。Service 会先检查用户是否存在，然后通过 `Ticket::getUserId` 查询工单列表，并按创建时间倒序返回。

## 7. GET /users/{userId}/ticket-replies 表达了什么含义？

`GET /users/{userId}/ticket-replies` 表示查询某个用户发表过的工单回复列表。

它关注的是“这个用户说过什么”，而不是“某张工单下有哪些回复”。

当前项目中，这个接口会：

- 先检查 `userId` 对应的用户是否存在。
- 再根据 `TicketReply::getUserId` 查询该用户发表过的回复。
- 最后按 `createdAt` 倒序返回。

这个接口适合用于用户个人中心、回复历史、客服处理记录等场景。

## 8. 为什么工单详情接口不应该只返回 Ticket？

`Ticket` 只是工单表实体，只包含工单本身的信息，例如标题、内容、状态、优先级、提交人 id。

但是前端展示工单详情时，通常还需要：

- 工单本身信息。
- 提交人信息。
- 工单下的回复列表。

如果详情接口只返回 `Ticket`，前端就需要再请求用户接口和回复接口，页面逻辑会变复杂，请求次数也会增加。

当前项目用 `TicketDetailVO` 返回组合数据：

```text
TicketDetailVO
  ticket  -> 工单本身
  user    -> 提交人信息
  replies -> 回复列表
```

所以 `GET /tickets/{id}/detail` 更适合用于工单详情页。

## 9. VO 和 Entity 有什么区别？

`Entity` 是数据库实体，通常和数据库表一一对应。

例如：

- `User` 对应 `user` 表。
- `Ticket` 对应 `ticket` 表。
- `TicketReply` 对应 `ticket_reply` 表。

`VO` 是返回给前端的视图对象，重点是满足页面展示需要，不要求和某张数据库表一一对应。

例如 `TicketDetailVO` 不对应一张表，而是组合了 `Ticket`、`User`、`List<TicketReply>`。这样可以让前端一次拿到详情页需要的数据。

简单理解：

- `Entity` 面向数据库。
- `VO` 面向接口返回和前端展示。

不要为了前端展示方便，把大量非本表字段塞进 Entity。更合适的做法是新建 VO 进行组合返回。

## 10. Service 层为什么需要同时调用 UserMapper、TicketMapper、TicketReplyMapper？

因为用户和工单关联功能不是单表查询，而是跨表业务逻辑。

不同 Mapper 的职责不同：

- `UserMapper` 用来查询用户，校验用户是否存在。
- `TicketMapper` 用来查询或操作工单。
- `TicketReplyMapper` 用来查询或操作工单回复。

Service 层负责把这些 Mapper 组合起来，形成完整业务流程。

例如查询工单详情时，`TicketService` 需要：

- 通过 `TicketMapper` 查询工单。
- 通过 `UserMapper` 查询提交人。
- 通过 `TicketReplyMapper` 查询回复列表。
- 最后组装成 `TicketDetailVO` 返回。

Controller 只负责接收 HTTP 请求和返回响应，不应该直接写复杂查询和组装逻辑。Mapper 只负责数据库访问，也不应该承担业务规则判断。

## 11. LambdaQueryWrapper 在按 userId 查询时起什么作用？

`LambdaQueryWrapper` 是 MyBatis-Plus 提供的条件构造器，用来用 Java 代码拼接 SQL 查询条件。

在当前项目中，按用户查询工单时使用了类似逻辑：

```java
LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Ticket::getUserId, userId);
wrapper.orderByDesc(Ticket::getCreatedAt);
```

它最终表达的 SQL 含义类似：

```sql
where user_id = ?
order by created_at desc
```

使用 `LambdaQueryWrapper` 的好处是：

- 不需要手写字段名字符串。
- `Ticket::getUserId` 和实体字段绑定，重构字段名时更安全。
- 查询条件更清晰，适合简单条件查询。

在 `GET /users/{userId}/ticket-replies` 中也一样，后端通过 `TicketReply::getUserId` 查询某个用户发表过的回复。

## 12. 这个功能为后续登录 JWT 和权限控制做了什么铺垫？

当前阶段接口中还需要前端传 `userId`，这是学习阶段常见写法。

后续接入登录和 JWT 后，后端可以从 token 中解析当前登录用户 id，而不是完全相信前端传来的 `userId`。

有了 `ticket.user_id` 和 `ticket_reply.user_id`，后续就可以实现这些能力：

- 创建工单时，直接使用当前登录用户 id 作为 `ticket.user_id`。
- 普通用户只能查看自己的工单。
- 普通用户只能查看或管理自己的回复。
- 客服或管理员可以查看全部工单和回复。
- 修改、删除、回复工单前，可以判断当前用户是否有权限。
- 操作日志和审计可以追踪到具体用户。

因此，当前的用户和工单关联功能是后续登录、JWT、角色权限控制的基础。


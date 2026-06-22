# 数据库基础概念说明

本文用初学者能理解的方式，解释当前项目里 `user`、`ticket`、`ticket_reply`、`operation_log` 相关的数据库概念。

阅读本文前，可以先知道当前项目的三张核心业务表：

```text
user          用户表
ticket        工单表
ticket_reply  工单回复表
```

它们之间的关系是：

```text
User 1 --- N Ticket
User 1 --- N TicketReply
Ticket 1 --- N TicketReply
```

## 1. 什么是主键？

主键就是一张表中每一行数据的唯一编号。

可以把主键理解成“身份证号”。比如用户表里有很多用户，数据库需要一个字段来唯一识别每个用户，这个字段就是主键。

在当前项目中，主键通常叫 `id`：

```text
user.id
ticket.id
ticket_reply.id
```

主键有几个特点：

- 每条数据都必须有主键。
- 主键不能重复。
- 主键通常不应该频繁修改。
- 后续其他表可以通过这个主键找到对应数据。

例如：

```text
user.id = 1 表示某一个具体用户
ticket.id = 1 表示某一张具体工单
```

## 2. 为什么 user、ticket、ticket_reply 都要有 id？

因为每张表都需要能准确找到自己的某一条数据。

`user` 表需要 `id`：

```text
用来唯一标识一个用户
```

`ticket` 表需要 `id`：

```text
用来唯一标识一张工单
```

`ticket_reply` 表需要 `id`：

```text
用来唯一标识一条回复
```

如果没有 `id`，就会出现这些问题：

- 查询某一条数据不方便。
- 修改某一条数据不准确。
- 删除某一条数据容易误删。
- 其他表很难和它建立关系。

所以每张核心业务表都应该有自己的主键。

## 3. 什么是外键思想？

外键思想就是：一张表通过某个字段，指向另一张表的主键。

例如：

```text
ticket.user_id 指向 user.id
```

这表示：这张工单属于哪个用户。

再比如：

```text
ticket_reply.ticket_id 指向 ticket.id
```

这表示：这条回复属于哪张工单。

注意，外键思想不一定等于数据库里已经写了 `FOREIGN KEY` 约束。当前项目先用字段命名和代码逻辑表达这种关系，暂时没有强制添加数据库外键约束。

简单理解：

```text
主键：我是谁
外键思想：我和谁有关
```

## 4. 为什么 ticket 表要有 user_id？

`ticket.user_id` 用来记录这张工单是谁创建的。

例如：

```text
ticket.id = 100
ticket.user_id = 1
```

含义是：

```text
id 为 100 的工单，是 id 为 1 的用户创建的
```

有了 `ticket.user_id`，后端才能实现：

- 查询某个用户创建过哪些工单。
- 普通用户只能查看自己的工单。
- 工单详情里显示提交人信息。
- 后续做权限控制时判断当前用户能不能访问这张工单。

如果 `ticket` 表没有 `user_id`，工单就不知道是谁提交的，也就无法做用户和工单的关联。

## 5. 为什么 ticket_reply 表要有 ticket_id？

`ticket_reply.ticket_id` 用来记录这条回复属于哪张工单。

例如：

```text
ticket_reply.id = 20
ticket_reply.ticket_id = 100
```

含义是：

```text
id 为 20 的回复，属于 id 为 100 的工单
```

有了 `ticket_id`，后端才能实现：

- 查询某张工单下的所有回复。
- 在工单详情页展示完整沟通过程。
- 区分不同工单下的回复。

如果回复表没有 `ticket_id`，所有回复就混在一起，后端不知道哪条回复属于哪张工单。

## 6. 为什么 ticket_reply 表还要有 user_id？

`ticket_reply.ticket_id` 只能说明“回复属于哪张工单”，但不能说明“回复是谁发的”。

所以还需要 `ticket_reply.user_id`。

例如：

```text
ticket_reply.ticket_id = 100
ticket_reply.user_id = 1
```

含义是：

```text
这条回复属于 100 号工单，并且是 1 号用户发表的
```

有了 `ticket_reply.user_id`，后端才能实现：

- 查询某个用户发表过哪些回复。
- 判断回复是谁发的。
- 区分用户回复、客服回复、管理员回复。
- 后续做审计和操作追踪。

所以 `ticket_reply` 同时需要：

```text
ticket_id：说明回复属于哪张工单
user_id：说明回复是谁发的
```

## 7. user、ticket、ticket_reply 是什么关系？

这三张表的关系是：

```text
User 1 --- N Ticket
User 1 --- N TicketReply
Ticket 1 --- N TicketReply
```

换成更直白的话：

- 一个用户可以创建多张工单。
- 一个用户可以发表多条回复。
- 一张工单可以有多条回复。
- 一条工单只属于一个提交用户。
- 一条回复只属于一张工单。
- 一条回复只由一个用户发表。

字段对应关系是：

```text
ticket.user_id 关联 user.id
ticket_reply.user_id 关联 user.id
ticket_reply.ticket_id 关联 ticket.id
```

## 8. 一对多关系是什么意思？

一对多关系就是：一条数据可以对应另一张表里的多条数据。

例如：

```text
一个用户可以创建多张工单
```

这就是：

```text
User 1 --- N Ticket
```

再比如：

```text
一张工单可以有多条回复
```

这就是：

```text
Ticket 1 --- N TicketReply
```

其中：

```text
1 表示一个
N 表示多个
```

所以一对多可以理解为：

```text
一个用户 -> 多张工单
一张工单 -> 多条回复
```

## 9. 什么是索引？

索引是数据库用来加快查询速度的数据结构。

可以把索引理解成书的目录。

如果一本书没有目录，你要找某个章节，就只能一页一页翻。  
如果有目录，你可以直接根据目录跳到对应位置。

数据库也是一样。

如果表里数据很多，没有索引时，数据库可能要一行一行查。  
有索引时，数据库可以更快找到符合条件的数据。

例如：

```sql
SELECT * FROM ticket WHERE user_id = 1;
```

如果 `ticket.user_id` 有索引，数据库查某个用户的工单会更快。

## 10. 为什么 ticket.user_id 适合加索引？

因为项目里经常会按用户查询工单。

例如普通用户登录后，只能看自己的工单。后端会查询：

```sql
SELECT * FROM ticket WHERE user_id = 当前用户ID;
```

如果 `ticket` 表数据越来越多，比如有几万条工单，没有索引时数据库可能要从头扫到尾。

给 `ticket.user_id` 加索引后，数据库可以更快找到某个用户的工单。

所以适合加：

```sql
CREATE INDEX idx_ticket_user_id ON ticket(user_id);
```

## 11. 为什么 ticket.status、priority、category 适合加索引？

因为工单列表经常会按这些字段筛选。

例如按状态筛选：

```sql
SELECT * FROM ticket WHERE status = 'OPEN';
```

例如按优先级筛选：

```sql
SELECT * FROM ticket WHERE priority = 'HIGH';
```

例如按分类筛选：

```sql
SELECT * FROM ticket WHERE category = 'ACCOUNT';
```

这些字段都是查询条件，尤其在分页查询中很常见，所以适合建立索引：

```sql
CREATE INDEX idx_ticket_status ON ticket(status);
CREATE INDEX idx_ticket_priority ON ticket(priority);
CREATE INDEX idx_ticket_category ON ticket(category);
```

索引的作用是让数据库更快筛出符合条件的工单。

## 12. 为什么 ticket_reply.ticket_id 适合加索引？

因为项目经常需要查询某张工单下的所有回复。

例如：

```sql
SELECT * FROM ticket_reply WHERE ticket_id = 100;
```

这个查询会出现在工单详情页。

如果 `ticket_reply.ticket_id` 没有索引，当回复数据很多时，数据库要遍历很多行才能找到某张工单的回复。

给它加索引后，查询某张工单下的回复会更快：

```sql
CREATE INDEX idx_reply_ticket_id ON ticket_reply(ticket_id);
```

## 13. created_at 和 updated_at 分别有什么用？

`created_at` 表示这条数据是什么时候创建的。

例如：

```text
工单创建时间
回复创建时间
用户注册时间
```

`updated_at` 表示这条数据最后一次是什么时候更新的。

例如：

```text
工单状态被修改
用户资料被修改
回复内容后续支持编辑
```

它们的区别是：

```text
created_at：第一次创建的时间，一般不变
updated_at：最后一次修改的时间，会随着更新变化
```

这些字段很有用：

- 可以按创建时间排序。
- 可以排查数据是什么时候产生的。
- 可以知道数据最近有没有被改过。
- 后续做审计、日志、统计时会用到。

## 14. 数据库字段为什么推荐用下划线命名？

数据库字段通常推荐使用下划线命名，也叫 snake_case。

例如：

```text
user_id
ticket_id
reply_type
created_at
updated_at
```

原因是：

- SQL 里下划线命名很常见。
- 字段名全小写，跨数据库更稳。
- 多个单词之间用 `_` 分隔，阅读清楚。
- 和很多数据库规范保持一致。

例如 `created_at` 一眼就能看出是“创建时间”。

## 15. Java 字段为什么推荐用驼峰命名？

Java 字段通常推荐使用驼峰命名，也叫 camelCase。

例如：

```text
userId
ticketId
replyType
createdAt
updatedAt
```

原因是：

- 这是 Java 社区的常见规范。
- Java 类、字段、方法一般都用驼峰。
- getter/setter 命名更自然。

例如：

```java
private Long userId;

public Long getUserId() {
    return userId;
}
```

数据库字段和 Java 字段命名不同没有问题，MyBatis-Plus 可以通过 `@TableField` 建立映射：

```java
@TableField("user_id")
private Long userId;
```

## 16. operation_log 表解决什么问题？

`operation_log` 表用于记录系统里发生过的重要操作。

例如：

- 谁登录了系统。
- 谁创建了工单。
- 谁回复了工单。
- 谁修改了工单状态。
- 谁删除了工单。

这些记录可以帮助后端回答：

```text
谁在什么时候做了什么？
```

例如一条日志可能表示：

```text
用户 3 在 2026-06-15 10:00 修改了 100 号工单状态
```

操作日志常用于：

- 问题追踪。
- 安全审计。
- 排查误操作。
- 统计系统使用情况。

当前项目只创建 `operation_log.sql`，暂时不实现业务代码。后续可以再新增 `OperationLogService`，在关键操作时写入日志。

## 17. 为什么初学阶段可以先不拆 role、permission 等 RBAC 表？

完整 RBAC 会涉及多张表，例如：

```text
role
permission
user_role
role_permission
```

它的好处是权限更灵活，但学习成本也更高。

初学阶段如果一开始就拆 RBAC，容易出现这些问题：

- 表太多，不容易理解主线。
- 登录、JWT、权限判断还没掌握，就先陷入复杂表关系。
- 写接口时需要同时理解角色、权限、关联表，负担较重。

当前项目的学习重点是：

```text
用户登录
JWT 识别当前用户
根据角色控制接口权限和数据权限
```

所以先不拆完整 RBAC，更适合循序渐进。

## 18. 为什么初学阶段可以先用 user.role 字段？

因为当前项目只有三种固定角色：

```text
USER
STAFF
ADMIN
```

用 `user.role` 一个字段就能表达：

```text
这个用户是什么角色
```

例如：

```text
user.role = USER
user.role = STAFF
user.role = ADMIN
```

这种方式的优点是：

- 表结构简单。
- 代码容易写。
- 查询用户时就能直接拿到角色。
- 适合学习登录和权限控制主线。

它的缺点是：

- 一个用户通常只能有一个角色。
- 权限点不能灵活配置。
- 如果角色越来越多，代码会变复杂。

所以当前阶段先用 `user.role` 是合理的。等项目更复杂后，再升级成完整 RBAC。

## 总结

当前项目的数据库设计可以这样理解：

```text
每张表用 id 作为主键
表和表之间通过 user_id、ticket_id 建立关系
常用查询字段加索引提升查询速度
created_at / updated_at 记录数据生命周期
operation_log 为后续审计和追踪做准备
初学阶段先用 user.role 实现简单权限
```

先把这些基础概念理解清楚，再学习完整 RBAC 会更顺。


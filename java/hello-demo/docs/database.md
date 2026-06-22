# 数据库表关系与字段设计说明

本文整理当前 Spring Boot 工单项目的数据库表结构、字段含义、表关系、索引设计和 SQL 文件使用方式。

当前项目先使用 `user.role` 字段实现 `USER`、`STAFF`、`ADMIN` 三种角色。后续如果要升级成完整 RBAC，可以扩展 `role`、`user_role`、`permission`、`role_permission` 表。

## 1. 核心表

当前项目核心表：

```text
user
ticket
ticket_reply
operation_log
```

各表职责：

- `user`：保存用户基础信息、登录账号、加密密码和角色。
- `ticket`：保存用户提交的工单。
- `ticket_reply`：保存工单下的回复记录。
- `operation_log`：保存注册、登录、创建工单、回复工单、修改状态、删除工单等关键操作日志。

## 2. 表关系

文字版 ER 图：

```text
User 1 --- N Ticket
User 1 --- N TicketReply
Ticket 1 --- N TicketReply
```

字段关系：

```text
ticket.user_id 关联 user.id
ticket_reply.user_id 关联 user.id
ticket_reply.ticket_id 关联 ticket.id
```

含义：

- 一个用户可以创建多个工单。
- 一个用户可以发表多条回复。
- 一个工单可以有多条回复。
- 一条回复只属于一个工单。
- 一条回复由一个用户发表。

当前 SQL 先使用字段和索引表达关系，没有强制添加数据库外键约束。这样更适合学习阶段，避免旧数据导入和重复初始化时被外键约束卡住。

## 3. user 表

`user` 表保存用户基础信息、登录认证信息和角色。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 用户主键，自增 |
| username | VARCHAR(50) | 登录用户名，唯一 |
| password | VARCHAR(100) | BCrypt 加密后的密码 |
| name | VARCHAR(50) | 姓名 |
| age | INT | 年龄 |
| email | VARCHAR(100) | 邮箱 |
| role | VARCHAR(30) | USER / STAFF / ADMIN |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

对应实体：`entity/User.java`。

注意：接口响应不要返回 `password`。当前用户详情接口返回 `UserInfoVO`。

## 4. ticket 表

`ticket` 表保存用户提交的工单。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 工单主键，自增 |
| title | VARCHAR(100) | 工单标题 |
| content | TEXT | 工单内容 |
| status | VARCHAR(30) | OPEN / PROCESSING / CLOSED |
| priority | VARCHAR(30) | LOW / MEDIUM / HIGH / URGENT |
| category | VARCHAR(50) | 工单分类 |
| user_id | BIGINT | 提交工单的用户 ID |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

对应实体：`entity/Ticket.java`。

状态含义：

```text
OPEN        待处理
PROCESSING 处理中
CLOSED      已关闭
```

创建工单时，`ticket.user_id` 由后端从 JWT 当前登录用户中获取，不接收前端传入。

## 5. ticket_reply 表

`ticket_reply` 表保存工单回复。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 回复主键，自增 |
| ticket_id | BIGINT | 所属工单 ID |
| user_id | BIGINT | 回复人用户 ID |
| content | TEXT | 回复内容 |
| reply_type | VARCHAR(30) | USER / STAFF / AI |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

对应实体：`entity/TicketReply.java`。

回复类型：

```text
USER  用户回复
STAFF 客服或管理员回复
AI    后续 AI 回复建议
```

当前新增回复时，前端不传 `replyType`，后端根据当前登录用户角色自动设置。

## 6. operation_log 表

`operation_log` 表保存系统关键操作记录。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 日志主键，自增 |
| user_id | BIGINT | 操作人用户 ID，登录失败且用户不存在时可为空 |
| operation_type | VARCHAR(50) | 操作类型 |
| business_type | VARCHAR(50) | 业务类型 |
| business_id | BIGINT | 业务数据 ID |
| content | VARCHAR(500) | 操作内容描述 |
| created_at | DATETIME | 创建时间 |

对应实体：`entity/OperationLog.java`。

当前操作类型：

```text
CREATE_TICKET
REPLY_TICKET
UPDATE_TICKET_STATUS
DELETE_TICKET
LOGIN_SUCCESS
LOGIN_FAILED
REGISTER_USER
```

当前业务类型：

```text
AUTH
USER
TICKET
TICKET_REPLY
```

## 7. 索引说明

建议索引：

```text
idx_ticket_user_id：用于按用户查询工单
idx_ticket_status：用于按状态筛选工单
idx_ticket_priority：用于按优先级筛选工单
idx_ticket_category：用于按分类筛选工单
idx_reply_ticket_id：用于查询某个工单下的回复
idx_reply_user_id：用于查询某个用户发表的回复
idx_operation_log_user_id：用于按用户查询日志
idx_operation_log_business：用于按业务类型和业务 ID 查询日志
idx_operation_log_operation_type：用于按操作类型查询日志
idx_operation_log_created_at：用于按创建时间排序日志
```

索引可以减少数据库扫描的数据量，让条件查询和分页更快。

执行 `CREATE INDEX` 前，请先用：

```sql
SHOW INDEX FROM ticket;
SHOW INDEX FROM ticket_reply;
SHOW INDEX FROM operation_log;
```

检查索引是否已存在，避免重复创建报错。

## 8. SQL 文件说明

SQL 文件目录：

```text
src/main/resources/sql
```

当前 SQL 文件：

| 文件 | 作用 |
|---|---|
| user.sql | 创建数据库和 user 表，并插入基础用户 |
| ticket.sql | 创建 ticket 表，并插入示例工单 |
| ticket_reply.sql | 创建 ticket_reply 表，并插入示例回复 |
| operation_log.sql | 创建 operation_log 表和日志索引 |
| schema_upgrade.sql | 旧库升级参考，包括字段和索引升级 |
| user_auth_update.sql | 旧 user 表补登录认证字段的升级参考 |
| operation_log_upgrade.sql | 旧 operation_log 表补 operation_type 索引 |

新库推荐执行顺序：

```text
1. user.sql
2. ticket.sql
3. ticket_reply.sql
4. operation_log.sql
```

旧库升级时，按缺失内容选择执行：

```text
user_auth_update.sql
schema_upgrade.sql
operation_log_upgrade.sql
```

升级脚本不包含 `DROP TABLE`、`TRUNCATE` 等清空数据语句。执行前仍建议先备份数据库。

## 9. Entity 字段映射检查

数据库字段使用下划线命名：

```text
user_id
ticket_id
reply_type
created_at
updated_at
```

Java 字段使用驼峰命名：

```text
userId
ticketId
replyType
createdAt
updatedAt
```

MyBatis-Plus 中使用 `@TableField` 显式映射：

```java
@TableField("user_id")
private Long userId;
```

当前实体和表字段对应关系：

- `User`：`id, username, password, name, age, email, role, createdAt, updatedAt`
- `Ticket`：`id, title, content, status, priority, category, userId, createdAt, updatedAt`
- `TicketReply`：`id, ticketId, userId, content, replyType, createdAt, updatedAt`
- `OperationLog`：`id, userId, operationType, businessType, businessId, content, createdAt`

## 10. RBAC 后续扩展

当前项目用 `user.role` 表示角色：

```text
USER
STAFF
ADMIN
```

这种方式简单，适合学习登录认证和权限控制主线。

如果后续升级成完整 RBAC，可以扩展：

```text
role
user_role
permission
role_permission
```

扩展后可以支持：

- 一个用户多个角色；
- 一个角色多个权限；
- 权限点动态配置；
- 菜单权限、按钮权限、接口权限分离。

## 11. Redis 与数据库关系

Redis 不需要 SQL。

当前 Redis 只缓存：

```text
user:detail:{id}
ticket:detail:{id}
```

MySQL 仍然是最终数据来源。修改或删除用户、工单、回复后，Service 会删除对应缓存，下一次查询再从 MySQL 加载最新数据。

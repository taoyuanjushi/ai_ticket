# 认证和权限控制说明

本文用初学者能理解的方式，解释当前项目中登录认证、角色权限和工单数据权限的设计。

当前项目的权限控制主要依赖这些类：

- `CurrentUserContext`：保存当前请求中的登录用户信息。
- `UserRole`：定义 `USER`、`STAFF`、`ADMIN` 三种角色。
- `PermissionUtil`：在 Service 层统一判断是否登录、是否有权限。
- `TicketService`：处理工单创建、查询、详情、状态修改、删除的权限。
- `TicketReplyService`：处理工单回复的权限。
- `UserService`：处理用户管理接口的权限。

## 1. 认证和权限有什么区别？

认证解决的是：你是谁。

例如用户登录时，后端会校验用户名和密码。校验成功后，后端返回 JWT Token。之后前端每次请求都带上这个 Token，后端就能知道当前请求是谁发起的。

权限解决的是：你能做什么。

例如同样已经登录：

- 普通用户可以创建工单，但不能删除工单。
- 客服可以修改工单状态，但不能管理用户。
- 管理员可以删除工单，也可以管理用户。

所以认证和权限的关系是：

```text
先认证：确认你是谁
再鉴权：判断你有没有资格做这件事
```

没有认证，就不知道当前用户是谁；不知道用户是谁，就无法准确判断权限。

## 2. USER、STAFF、ADMIN 三种角色分别能做什么？

当前项目中有三种角色。

`USER` 是普通用户，主要代表提交工单的人：

- 可以创建自己的工单。
- 可以查看自己的工单。
- 可以回复自己的工单。
- 不能查看别人的工单。
- 不能修改工单状态。
- 不能删除工单。
- 不能管理用户。

`STAFF` 是客服人员，主要负责处理工单：

- 可以查看所有工单。
- 可以查看任意工单详情。
- 可以回复所有工单。
- 可以修改工单状态。
- 不能删除工单。
- 不能管理用户。

`ADMIN` 是管理员，拥有最高权限：

- 可以查看所有工单。
- 可以回复所有工单。
- 可以修改工单状态。
- 可以删除工单。
- 可以管理用户。

简单理解：

```text
USER  只能处理自己的数据
STAFF 可以处理所有工单，但不能做危险管理操作
ADMIN 可以做全部管理操作
```

## 3. 什么是接口权限？

接口权限指的是：某个接口，哪些角色可以访问。

例如：

```text
DELETE /tickets/{id}
```

这是删除工单接口。当前项目只允许 `ADMIN` 访问。即使 `USER` 或 `STAFF` 已经登录，只要角色不够，也会返回无权限。

再比如：

```text
PUT /tickets/{id}/status
```

这是修改工单状态接口。当前项目允许 `STAFF` 和 `ADMIN` 访问，不允许 `USER` 访问。

这类判断关注的是“这个接口能不能调用”，所以叫接口权限。

## 4. 什么是数据权限？

数据权限指的是：同一个接口，不同用户能看到或操作的数据范围不同。

例如：

```text
GET /tickets
```

这个接口是查询工单列表。

`USER` 调用时，只能看到自己的工单。  
`STAFF` 调用时，可以看到所有工单。  
`ADMIN` 调用时，也可以看到所有工单。

接口是同一个，但返回的数据范围不同，这就是数据权限。

再比如：

```text
GET /tickets/{id}
```

普通用户访问自己的工单，可以成功。普通用户访问别人的工单，即使这个工单真实存在，也不能返回给他。

## 5. 为什么普通用户只能查看自己的工单？

因为工单里可能包含用户的隐私信息，例如账号问题、联系方式、故障描述、业务数据等。

如果普通用户可以查看别人的工单，就会出现这些问题：

- 用户 A 能看到用户 B 的问题描述。
- 用户 A 可能知道用户 B 的邮箱、账号或系统使用情况。
- 恶意用户可以遍历工单 id，批量查看别人的数据。
- 系统失去最基本的数据隔离能力。

所以当前项目在 `TicketService` 中做了数据权限判断：

```text
如果当前角色是 USER，并且工单的 userId 不是当前用户 id，就拒绝访问。
```

这样可以保证普通用户只能看到自己创建的工单。

## 6. 为什么创建工单不能再让前端传 userId？

因为前端传来的数据不能完全相信。

如果创建工单时让前端传 `userId`，恶意用户可以这样做：

```json
{
  "title": "伪造工单",
  "content": "这不是我自己的工单",
  "userId": 1
}
```

如果后端直接相信这个 `userId`，用户就可以伪造成别人创建工单。

正确做法是：

```text
前端只传工单内容
后端从 JWT Token 中解析当前登录用户 id
后端把这个 id 写入 ticket.user_id
```

当前项目中，创建工单时通过 `PermissionUtil.requireLoginUserId()` 获取当前登录用户 id，然后写入 `ticket.userId`。即使前端偷偷传了 `userId`，后端也会忽略。

## 7. CurrentUserContext 在权限控制中起什么作用？

`CurrentUserContext` 用来保存当前请求里的登录用户信息。

请求流程可以理解为：

```text
前端携带 JWT Token
JwtInterceptor 解析 Token
解析出 userId、username、role
保存到 CurrentUserContext
Service 从 CurrentUserContext 读取当前用户信息
Service 根据用户角色和用户 id 判断权限
请求结束后清理 CurrentUserContext
```

它保存的信息主要包括：

- 当前用户 id
- 当前用户名
- 当前用户角色

例如工单创建时，Service 不需要从请求体拿 `userId`，而是从 `CurrentUserContext` 中拿当前登录用户 id。

## 8. 为什么权限判断应该写在 Service 层？

Controller 的主要职责是接收 HTTP 请求、绑定参数、调用 Service、返回结果。

Service 的主要职责是处理业务规则。

权限判断属于业务规则。例如：

- 普通用户只能查看自己的工单。
- STAFF 可以修改工单状态。
- ADMIN 才能删除工单。

这些规则和 HTTP 请求形式无关，属于业务本身，所以应该放在 Service 层。

如果把权限判断写在 Controller 中，会有几个问题：

- Controller 会出现大量 `if-else`，代码越来越乱。
- 同一个 Service 方法如果被多个 Controller 调用，容易漏掉权限判断。
- 后续如果改接口路径，权限逻辑也容易被误改。
- 业务规则分散在多个地方，不方便维护。

所以当前项目把权限判断集中放在 Service 层，通过 `PermissionUtil` 统一调用。

## 9. 普通用户访问别人的工单时，后端应该返回什么？

如果工单存在，但不属于当前普通用户，后端应该返回 403。

示例返回：

```json
{
  "code": 403,
  "message": "无权限访问该工单",
  "data": null
}
```

这里的 `403` 表示：你已经登录了，但没有权限访问这个资源。

这和 `401` 不一样：

- `401`：没有登录，或者 Token 无效。
- `403`：已经登录，但权限不够。

例如普通用户不带 Token 访问接口，应该返回 `401 请先登录`。  
普通用户带了 Token，但访问别人的工单，应该返回 `403 无权限访问该工单`。

## 10. STAFF 和 ADMIN 在工单模块中的权限有什么区别？

`STAFF` 和 `ADMIN` 都可以处理工单，但权限范围不完全一样。

它们相同的地方：

- 都可以查看所有工单。
- 都可以查看任意工单详情。
- 都可以回复任意工单。
- 都可以修改工单状态。

它们不同的地方：

- `STAFF` 不能删除工单。
- `ADMIN` 可以删除工单。

为什么这样设计？

客服人员主要负责处理问题，例如回复用户、推进状态、关闭工单。删除数据属于高风险操作，一旦误删可能导致工单记录丢失，所以只交给管理员。

## 11. 为什么删除工单只允许 ADMIN？

删除是危险操作。

工单通常代表用户反馈、处理过程、客服回复、状态变化等历史记录。删除工单可能带来这些问题：

- 用户反馈记录丢失。
- 客服处理记录丢失。
- 后续无法追踪问题过程。
- 误删后很难恢复。
- 审计和统计数据不完整。

所以当前项目只允许 `ADMIN` 删除工单。`USER` 和 `STAFF` 即使已经登录，也会返回：

```json
{
  "code": 403,
  "message": "无权限操作",
  "data": null
}
```

实际项目中，很多系统甚至不会真的物理删除工单，而是使用“软删除”，例如增加 `deleted` 字段，把数据标记为已删除。

## 12. 后续如果要升级成完整 RBAC，需要增加哪些表？

当前项目是简单角色模型：

```text
user.role = USER / STAFF / ADMIN
```

这种方式适合学习阶段和小系统，但如果系统变复杂，就需要升级成 RBAC。

RBAC 的意思是：基于角色的访问控制。

完整 RBAC 通常会增加这些表：

### 1. role 角色表

保存系统中有哪些角色。

示例字段：

```text
id
role_code
role_name
description
created_at
updated_at
```

例如：

```text
USER
STAFF
ADMIN
```

以后也可以增加：

```text
SUPPORT_MANAGER
AUDITOR
```

### 2. permission 权限表

保存系统中有哪些权限点。

示例字段：

```text
id
permission_code
permission_name
description
```

例如：

```text
ticket:view
ticket:create
ticket:reply
ticket:update_status
ticket:delete
user:manage
```

### 3. user_role 用户角色关联表

保存某个用户拥有哪些角色。

示例字段：

```text
id
user_id
role_id
```

有了这张表，一个用户就可以拥有多个角色。

### 4. role_permission 角色权限关联表

保存某个角色拥有哪些权限。

示例字段：

```text
id
role_id
permission_id
```

例如：

```text
STAFF -> ticket:view
STAFF -> ticket:reply
STAFF -> ticket:update_status
ADMIN -> ticket:delete
ADMIN -> user:manage
```

### 5. 可选：menu 菜单表

如果前端菜单也要按权限显示，可以增加菜单表。

示例字段：

```text
id
menu_name
path
permission_code
parent_id
sort
```

这样前端可以根据当前用户权限，只显示他能访问的页面。

## 总结

认证是判断“你是谁”，权限是判断“你能做什么”。

当前项目的权限控制可以总结为：

```text
JWT 负责识别当前用户
CurrentUserContext 保存当前用户信息
UserRole 定义角色
PermissionUtil 统一做权限判断
Service 层负责业务权限规则
```

普通用户只能操作自己的数据，客服可以处理工单，管理员负责高风险管理操作。这样的设计既能满足学习阶段的功能，也为后续升级完整 RBAC 打下基础。


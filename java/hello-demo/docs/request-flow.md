# 核心请求流程说明

本文档从请求进入后端开始，说明当前项目中登录、JWT、工单、回复、权限、日志和缓存的真实调用链。

## 1. 登录流程

```text
Postman 提交 username/password
↓
AuthController 接收 POST /auth/login
↓
AuthService 根据 username 查询 user 表
↓
BCrypt 校验密码
↓
密码正确：JwtUtil 生成 Token
↓
OperationLogService 记录 LOGIN_SUCCESS
↓
Result 返回 token
```

登录失败时：

```text
用户名不存在或密码错误
↓
OperationLogService 记录 LOGIN_FAILED
↓
AuthService 抛出 BusinessException
↓
GlobalExceptionHandler 返回统一失败 JSON
```

登录成功响应中包含 `token`、`userId`、`username`、`role`。

## 2. JWT 认证流程

```text
Postman 请求接口时携带 Authorization: Bearer token
↓
JwtInterceptor 拦截请求
↓
检查 Header 是否存在，格式是否正确
↓
JwtUtil 解析 token
↓
读取 userId、username、role
↓
CurrentUserContext 保存当前用户信息
↓
请求进入 Controller
↓
请求结束后清理 CurrentUserContext
```

放行接口：

```text
POST /auth/register
POST /auth/login
/error
```

其他接口都需要 Token。

## 3. 创建工单流程

```text
Postman 请求 POST /tickets，不传 userId
↓
JwtInterceptor 校验 Token
↓
CurrentUserContext 保存当前用户
↓
TicketController 接收请求体 TicketCreateDTO
↓
TicketService 从 CurrentUserContext 获取当前用户 ID
↓
设置 ticket.userId
↓
校验用户是否存在
↓
设置默认状态 OPEN
↓
TicketMapper 插入 MySQL
↓
OperationLogService 记录 CREATE_TICKET
↓
Result 返回创建成功
```

关键点：

- 前端不能决定 `userId`。
- 工单提交人必须以后端解析出的 JWT 当前用户为准。
- 创建成功后才记录成功日志，因为插入后才有工单 id。

## 4. 查询工单详情流程

```text
Postman 请求 GET /tickets/{id}/detail
↓
JwtInterceptor 校验 Token
↓
TicketController 调用 TicketService.getTicketDetail(id)
↓
TicketService 拼接 Redis key：ticket:detail:{id}
↓
先查 Redis
↓
Redis 命中：从缓存中的 Ticket 取 userId，继续校验权限
↓
权限通过：返回 TicketDetailVO
```

Redis 未命中时：

```text
查询 ticket 表
↓
校验工单是否存在
↓
校验当前用户是否可以查看该工单
↓
查询 user 表，获取提交人信息
↓
查询 ticket_reply 表，获取回复列表
↓
组装 TicketDetailVO
↓
写入 Redis，TTL 10 分钟
↓
Result 返回
```

关键点：

- 缓存不能绕过权限。
- `TicketDetailVO.user` 使用 `UserInfoVO`，不返回 `password`。

## 5. 修改工单状态流程

```text
Postman 请求 PUT /tickets/{id}/status
↓
JwtInterceptor 校验 Token
↓
TicketController 接收 TicketStatusUpdateDTO
↓
TicketService 判断 STAFF / ADMIN 权限
↓
查询工单是否存在
↓
校验目标状态是否合法
↓
校验状态流转规则
↓
TicketMapper 更新状态
↓
删除 ticket:detail:{id} Redis 缓存
↓
OperationLogService 记录 UPDATE_TICKET_STATUS
↓
Result 返回最新工单
```

当前状态流转规则：

```text
OPEN -> PROCESSING
OPEN -> CLOSED
PROCESSING -> CLOSED
CLOSED 不允许继续修改状态
```

## 6. 回复工单流程

```text
Postman 请求 POST /tickets/{ticketId}/replies，不传 userId 和 replyType
↓
JwtInterceptor 校验 Token
↓
TicketReplyController 接收 TicketReplyCreateDTO
↓
TicketReplyService 查询工单
↓
判断工单是否存在
↓
判断工单是否 CLOSED
↓
判断当前用户是否有权限回复
↓
根据角色自动设置 replyType
↓
TicketReplyMapper 插入回复
↓
OperationLogService 记录 REPLY_TICKET
↓
删除 ticket:detail:{ticketId} Redis 缓存
↓
Result 返回回复成功
```

权限规则：

- `USER` 只能回复自己的工单。
- `STAFF` / `ADMIN` 可以回复任意工单。
- `CLOSED` 工单不允许继续回复。

## 7. 操作日志流程

操作日志第一版没有使用 AOP，而是在关键 Service 成功路径中直接调用：

```java
operationLogService.record(...)
```

当前记录：

```text
REGISTER_USER
LOGIN_SUCCESS
LOGIN_FAILED
CREATE_TICKET
REPLY_TICKET
UPDATE_TICKET_STATUS
DELETE_TICKET
```

查询操作日志：

```text
Postman 请求 GET /operation-logs
↓
JwtInterceptor 校验 Token
↓
OperationLogController
↓
OperationLogService.requireAdmin()
↓
按 page、size、userId、operationType、businessType 条件查询
↓
返回 PageResult
```

## 8. 权限控制流程

认证、接口权限、数据权限的区别：

```text
认证：判断你是谁
接口权限：判断你能不能访问这个接口
数据权限：判断你能不能操作这条具体数据
```

角色规则：

```text
USER 只能操作自己的工单和回复
STAFF 可以查看和处理所有工单，但不能删除工单和管理用户
ADMIN 拥有全部权限
```

代码分工：

- `JwtInterceptor`：负责认证，解析 Token。
- `CurrentUserContext`：保存当前请求用户。
- `PermissionUtil`：提供角色判断工具。
- `Service`：执行接口权限和数据权限判断。

## 9. Redis 缓存流程

用户详情：

```text
GET /users/{id}
↓
权限校验
↓
查 user:detail:{id}
↓
命中则返回 UserInfoVO
↓
未命中则查 MySQL
↓
写入 Redis，TTL 30 分钟
```

工单详情：

```text
GET /tickets/{id}/detail
↓
查 ticket:detail:{id}
↓
命中后仍校验权限
↓
未命中则查 MySQL
↓
组装 TicketDetailVO
↓
写入 Redis，TTL 10 分钟
```

缓存删除：

```text
修改用户、删除用户 -> 删除 user:detail:{id}
修改工单、修改状态、删除工单、新增回复 -> 删除 ticket:detail:{id}
```

## 10. 统一响应和异常流程

正常流程：

```text
Controller 调用 Service
↓
Service 返回业务数据
↓
Controller 使用 Result.success(...)
↓
前端 / Postman 收到统一 JSON
```

异常流程：

```text
Service 抛出 BusinessException
↓
GlobalExceptionHandler 捕获
↓
Result.fail(code, message)
↓
返回统一失败 JSON
```

参数校验：

```text
DTO 字段使用 @NotBlank / @Email 等注解
↓
Controller 使用 @Valid
↓
参数不合法时 GlobalExceptionHandler 返回 400
```

## 11. Java 调用 Python AI 服务流程

当前第一版只打通 Java 后端到 Python AI 服务的基础 HTTP 通信，不操作 Ticket、TicketReply 或 User 数据。

```text
Postman 请求 POST /ai/chat
↓
JwtInterceptor 校验 Token
↓
AiController 接收 message
↓
AiService 调用 AiClient
↓
AiClient 通过 RestTemplate 请求 Python /agent/chat
↓
Python 返回 JSON
↓
Java 使用 Result 包装返回
```

相关接口和配置：

```text
Java 入口：POST /ai/chat
Python 入口：POST /agent/chat
配置项：ai.service.base-url=http://127.0.0.1:8001
```

分层职责：

- `AiController`：只负责接收请求和返回统一 `Result`。
- `AiService`：当前只做转发，后续 AI 业务编排放在这里。
- `AiClient`：只负责通过 HTTP 调用 Python AI 服务。
- `RestTemplateConfig`：配置连接超时 3 秒、读取超时 10 秒。

异常处理：

```text
Python 服务不可用或超时
↓
AiClient 抛出 BusinessException
↓
GlobalExceptionHandler 返回统一失败 JSON
```

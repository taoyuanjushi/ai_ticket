# API 接口文档

本文档整理当前后端真实存在的 REST API。项目默认地址：

```text
http://localhost:8080
```

除 `/auth/register`、`/auth/login`、`/error` 外，其余接口都会经过 JWT 拦截器校验。

## 1. 通用响应格式

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": 400,
  "message": "参数错误",
  "data": null
}
```

状态码含义：

| code | 含义 |
|---|---|
| 200 | 成功 |
| 400 | 参数错误或业务错误 |
| 401 | 未登录 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 系统异常 |

认证 Header：

```http
Authorization: Bearer <token>
```

## 2. Auth 接口

### POST /auth/register

功能：注册用户。  
是否需要 Token：否。

请求体：

```json
{
  "username": "user01",
  "password": "User@123456",
  "name": "User One",
  "age": 20,
  "email": "user01@example.com"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "id": 1,
    "username": "user01",
    "name": "User One",
    "email": "user01@example.com",
    "role": "USER"
  }
}
```

失败响应示例：

```json
{
  "code": 400,
  "message": "用户名已存在",
  "data": null
}
```

### POST /auth/login

功能：登录并获取 JWT Token。  
是否需要 Token：否。

请求体：

```json
{
  "username": "user01",
  "password": "User@123456"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "jwt-token",
    "userId": 1,
    "username": "user01",
    "role": "USER"
  }
}
```

失败响应示例：

```json
{
  "code": 400,
  "message": "用户名或密码错误",
  "data": null
}
```

### GET /auth/me

功能：查询当前登录用户。  
是否需要 Token：是。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "user01",
    "name": "User One",
    "email": "user01@example.com",
    "role": "USER"
  }
}
```

失败响应示例：

```json
{
  "code": 401,
  "message": "请先登录",
  "data": null
}
```

## 3. User 接口

权限说明：

- `ADMIN` 可以查询列表、创建用户、修改用户、删除用户。
- `USER` 只能查看自己的用户详情。
- 用户相关工单和回复查询中，`STAFF` / `ADMIN` 可以查看任意用户数据，`USER` 只能查看自己。

### GET /users

功能：查询用户列表。  
是否需要 Token：是。  
权限：`ADMIN`。

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "user01",
      "name": "User One",
      "age": 20,
      "email": "user01@example.com",
      "role": "USER"
    }
  ]
}
```

### GET /users/{id}

功能：查询用户详情。  
是否需要 Token：是。  
权限：`ADMIN` 可查任意用户，普通用户只能查自己。

成功响应不包含 `password`：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "user01",
    "name": "User One",
    "age": 20,
    "email": "user01@example.com",
    "role": "USER"
  }
}
```

### POST /users

功能：管理员新增用户。  
是否需要 Token：是。  
权限：`ADMIN`。

请求体：

```json
{
  "username": "staff01",
  "password": "Staff@123456",
  "name": "Staff One",
  "age": 25,
  "email": "staff01@example.com",
  "role": "STAFF"
}
```

### PUT /users/{id}

功能：管理员修改用户。  
是否需要 Token：是。  
权限：`ADMIN`。

请求体：

```json
{
  "username": "staff01",
  "name": "Staff One",
  "age": 26,
  "email": "staff01@example.com",
  "role": "STAFF"
}
```

### DELETE /users/{id}

功能：管理员删除用户。  
是否需要 Token：是。  
权限：`ADMIN`。

成功响应：

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

### GET /users/{userId}/tickets

功能：查询某个用户创建的工单。  
是否需要 Token：是。

权限：

- `USER` 只能查自己的工单。
- `STAFF` / `ADMIN` 可以查任意用户工单。

### GET /users/{userId}/ticket-replies

功能：查询某个用户发表的工单回复。  
是否需要 Token：是。

权限：

- `USER` 只能查自己的回复。
- `STAFF` / `ADMIN` 可以查任意用户回复。

## 4. Ticket 接口

权限说明：

- 创建工单不需要传 `userId`，后端从 JWT 中获取当前用户。
- `USER` 只能查看自己的工单。
- `STAFF` / `ADMIN` 可以查看所有工单。
- 只有 `STAFF` / `ADMIN` 可以修改工单和修改状态。
- 只有 `ADMIN` 可以删除工单。

### GET /tickets

功能：分页条件查询工单。  
是否需要 Token：是。

请求示例：

```http
GET /tickets?page=1&size=10&status=OPEN&priority=HIGH&category=ACCOUNT&keyword=login
```

查询参数：

| 参数 | 说明 |
|---|---|
| page | 页码，默认 1 |
| size | 每页数量，默认 10，最大 100 |
| status | 工单状态：OPEN / PROCESSING / CLOSED |
| priority | 优先级：LOW / MEDIUM / HIGH / URGENT |
| category | 工单分类 |
| keyword | 模糊查询 title / content |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 10
  }
}
```

### GET /tickets/{id}

功能：查询单个工单。  
是否需要 Token：是。

### GET /tickets/{id}/detail

功能：查询工单详情，包括工单本身、提交人信息和回复列表。  
是否需要 Token：是。  
说明：该接口使用 Redis 缓存 `ticket:detail:{id}`，命中缓存后仍会校验数据权限。

### POST /tickets

功能：创建工单。  
是否需要 Token：是。

请求体：

```json
{
  "title": "登录失败",
  "content": "账号无法登录",
  "priority": "HIGH",
  "category": "ACCOUNT"
}
```

说明：请求体不需要 `userId`。

### PUT /tickets/{id}

功能：修改工单基本信息。  
是否需要 Token：是。  
权限：`STAFF` / `ADMIN`。

请求体：

```json
{
  "title": "登录失败",
  "content": "账号仍然无法登录",
  "priority": "HIGH",
  "status": "PROCESSING",
  "category": "ACCOUNT"
}
```

### PUT /tickets/{id}/status

功能：修改工单状态。  
是否需要 Token：是。  
权限：`STAFF` / `ADMIN`。

请求体：

```json
{
  "status": "PROCESSING"
}
```

状态流转规则：

```text
OPEN -> PROCESSING
OPEN -> CLOSED
PROCESSING -> CLOSED
CLOSED 不允许继续修改状态
```

### DELETE /tickets/{id}

功能：删除工单。  
是否需要 Token：是。  
权限：`ADMIN`。

## 5. TicketReply 接口

### GET /tickets/{ticketId}/replies

功能：查询某个工单下的回复。  
是否需要 Token：是。

权限：

- `USER` 只能查看自己工单下的回复。
- `STAFF` / `ADMIN` 可以查看任意工单回复。

### POST /tickets/{ticketId}/replies

功能：新增工单回复。  
是否需要 Token：是。

请求体：

```json
{
  "content": "请尝试重置密码。"
}
```

说明：

- 不需要传 `userId`。
- 不需要传 `replyType`。
- 后端根据当前用户和角色自动设置。
- `USER` 只能回复自己的工单。
- `STAFF` / `ADMIN` 可以回复任意工单。
- `CLOSED` 工单不允许继续回复。

## 6. OperationLog 接口

### GET /operation-logs

功能：分页查询操作日志。  
是否需要 Token：是。  
权限：仅 `ADMIN`。

请求示例：

```http
GET /operation-logs?page=1&size=10&userId=1&businessType=TICKET&operationType=CREATE_TICKET
```

查询参数：

| 参数 | 说明 |
|---|---|
| page | 页码，默认 1 |
| size | 每页数量，默认 10，最大 100 |
| userId | 按操作人筛选 |
| businessType | AUTH / USER / TICKET / TICKET_REPLY |
| operationType | CREATE_TICKET / REPLY_TICKET / UPDATE_TICKET_STATUS / DELETE_TICKET / LOGIN_SUCCESS / LOGIN_FAILED / REGISTER_USER |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "operationType": "CREATE_TICKET",
        "businessType": "TICKET",
        "businessId": 1,
        "content": "用户创建了工单 #1",
        "createdAt": "2026-06-15T10:00:00"
      }
    ],
    "total": 1,
    "page": 1,
    "size": 10
  }
}
```

## 7. AI 接口

### POST /ai/chat

功能：Java 后端统一 AI 对话入口。  
是否需要 Token：是。  
说明：当前第一版只调用 Python AI 服务 `/agent/chat`，并把 Python 返回的 JSON 原样包装到统一 `Result` 的 `data` 中返回。

请求体：

```json
{
  "message": "查一下所有处理中工单"
}
```

Java 调用的 Python 地址由配置项控制：

```properties
ai.service.base-url=http://127.0.0.1:8001
```

实际请求路径：

```http
POST http://127.0.0.1:8001/agent/chat
```

成功响应示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "我是智能工单助手，可以帮你查询、创建、修改和总结工单。"
  }
}
```

如果 Python AI 服务不可用，会返回统一错误：

```json
{
  "code": 500,
  "message": "AI服务连接超时或不可用",
  "data": null
}
```

如果 `message` 为空，会返回参数校验错误：

```json
{
  "code": 400,
  "message": "message不能为空",
  "data": null
}
```

### AI Reserved 接口

以下接口仅作为后续规划，不属于当前可用 API：

```http
POST /ai/tickets/{id}/reply-suggestion
POST /ai/tickets/{id}/summary
POST /ai/tickets/{id}/classify
POST /ai/knowledge/ask
```

后续可以由 Java 后端调用 Python FastAPI AI 服务实现，再把 AI 回复建议保存为 `TicketReply`，并设置：

```text
replyType = AI
```

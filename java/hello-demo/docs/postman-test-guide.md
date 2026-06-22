# Postman 测试指南

本文档说明如何用 Postman 测试当前 Spring Boot 后端项目。

## 1. 导入 postman_collection.json

1. 打开 Postman。
2. 点击 `Import`。
3. 选择项目根目录下的 `postman_collection.json`。
4. 导入后可以看到这些分组：

```text
Auth
User
Ticket
TicketReply
OperationLog
AI Reserved
```

## 2. 配置 baseUrl

Postman Collection 已提供变量：

```text
baseUrl = http://localhost:8080
```

如果后端端口改成 8081，把变量改成：

```text
http://localhost:8081
```

## 3. 启动依赖服务

启动 MySQL，并执行 SQL 初始化。

启动 Redis：

```bash
docker start redis-study
```

如果容器不存在：

```bash
docker run -d --name redis-study -p 6379:6379 redis:7
```

检查 Redis：

```bash
docker exec -it redis-study redis-cli
ping
```

返回 `PONG` 表示正常。

## 4. 启动后端

```bash
mvn clean compile
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

## 5. 准备 USER / STAFF / ADMIN 三种账号

推荐先用注册接口创建三个账号：

```text
user01
staff01
admin01
```

然后在 MySQL 中手动设置角色：

```sql
UPDATE user SET role = 'USER' WHERE username = 'user01';
UPDATE user SET role = 'STAFF' WHERE username = 'staff01';
UPDATE user SET role = 'ADMIN' WHERE username = 'admin01';
```

## 6. 登录并保存 Token

分别调用：

```text
Auth / Login User
```

登录后，从响应中复制：

```text
data.token
```

设置到 Collection Variables：

```text
USER_TOKEN
STAFF_TOKEN
ADMIN_TOKEN
```

后续请求 Header 已经使用：

```http
Authorization: Bearer {{USER_TOKEN}}
```

或：

```http
Authorization: Bearer {{ADMIN_TOKEN}}
```

根据接口权限不同，选择不同 Token。

## 7. 推荐测试顺序

1. 注册 USER。
2. 注册 STAFF。
3. 注册 ADMIN。
4. 数据库手动设置 `role`。
5. 登录 USER，获取 `USER_TOKEN`。
6. 登录 STAFF，获取 `STAFF_TOKEN`。
7. 登录 ADMIN，获取 `ADMIN_TOKEN`。
8. USER 创建工单。
9. 设置 `ticketId` 为刚创建的工单 id。
10. USER 查询自己的工单。
11. STAFF 查询所有工单。
12. STAFF 修改工单状态。
13. USER 回复自己的工单。
14. STAFF 回复任意工单。
15. ADMIN 查询操作日志。
16. ADMIN 删除工单。
17. USER 访问别人数据，验证返回 403。

## 8. 主要接口测试说明

### 注册用户

```text
Auth / Register User
```

请求体示例：

```json
{
  "username": "user01",
  "password": "User@123456",
  "name": "User One",
  "age": 20,
  "email": "user01@example.com"
}
```

### 登录用户

```text
Auth / Login User
```

登录成功后保存 token。

### 创建工单

```text
Ticket / Create Ticket
```

使用 `USER_TOKEN`。

请求体不传 `userId`：

```json
{
  "title": "登录失败",
  "content": "账号无法登录",
  "priority": "HIGH",
  "category": "ACCOUNT"
}
```

### 查询工单详情并验证 Redis

```text
Ticket / Get Ticket Full Detail
```

请求成功后，Redis 中应出现：

```text
ticket:detail:{ticketId}
```

可以用：

```bash
docker exec -it redis-study redis-cli
keys *
ttl ticket:detail:1
```

### 修改状态并验证缓存删除

```text
Ticket / Update Ticket Status
```

使用 `STAFF_TOKEN` 或 `ADMIN_TOKEN`。

修改成功后，`ticket:detail:{ticketId}` 应被删除。

### 查询操作日志

```text
OperationLog / Get Operation Logs
```

使用 `ADMIN_TOKEN`。

普通用户访问该接口应返回：

```json
{
  "code": 403,
  "message": "无权限操作",
  "data": null
}
```

## 9. 常见错误说明

### 401 请先登录

没有设置 Token，或者 Header 格式不是：

```http
Authorization: Bearer <token>
```

### 403 无权限操作

当前 Token 对应角色没有权限。例如 USER 查询操作日志、删除工单、查看别人的工单。

### 400 参数校验失败

请求体缺少必填字段，或者字段格式不正确。检查 JSON 和接口文档。

### 404 资源不存在

用户、工单或回复对应数据不存在。检查 `userId`、`ticketId` 是否正确。

### 500 系统异常

优先检查控制台日志。常见原因包括 MySQL 表字段缺失、Redis 未启动、数据库连接失败。

### Token 已过期或无效

重新调用登录接口，复制新的 token 到 Postman 变量。

## 10. AI Reserved 分组说明

`AI Reserved` 分组中的接口当前后端暂未实现，只用于展示后续扩展计划。调用这些接口可能返回 401、404 或未实现相关响应。

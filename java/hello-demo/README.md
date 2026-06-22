# 智能工单管理系统后端

这是一个基于 Spring Boot + MySQL + Redis + JWT 的企业工单管理系统后端项目。

项目支持用户登录注册、JWT 认证、USER / STAFF / ADMIN 角色权限控制、工单管理、工单回复、状态流转、操作日志、Redis 缓存、统一响应、统一异常处理和 Postman 接口测试。

当前项目没有前端，所有功能通过后端 REST API 和 Postman 测试。当前版本已提供 Java 调用 Python AI 服务的基础通信接口，后续可以继续扩展 AI 工单分类、AI 回复建议、AI 工单总结、AI 知识库问答等能力。

## 技术栈

- Java 21
- Spring Boot 4.0.6
- Spring WebMVC
- MyBatis-Plus
- MySQL 8+
- Redis 7+
- JWT
- Validation
- Maven 3.9+
- Postman
- Docker，可选，用于启动 Redis

## 功能模块

### Auth 登录注册模块

提供注册、登录和查询当前登录用户接口。登录成功后返回 JWT Token，后续接口通过 `Authorization: Bearer <token>` 访问。

### User 用户模块

提供用户列表、用户详情、用户新增、修改、删除，以及查询用户工单和用户回复记录。用户详情返回 `UserInfoVO`，不会返回 `password`。

### Ticket 工单模块

提供工单创建、分页条件查询、详情查询、修改、删除和状态流转。创建工单时不需要前端传 `userId`，后端从 JWT 中读取当前登录用户。

### TicketReply 工单回复模块

提供工单回复创建和查询。新增回复时不需要传 `userId` 和 `replyType`，后端根据当前登录用户和角色自动设置。

### Permission 权限控制

项目使用 `USER`、`STAFF`、`ADMIN` 三种角色。`USER` 只能查看和操作自己的数据，`STAFF` 可以处理工单，`ADMIN` 拥有用户管理、删除工单、查看操作日志等权限。

### OperationLog 操作日志模块

记录注册、登录成功、登录失败、创建工单、回复工单、修改状态、删除工单等关键操作。操作日志只允许 `ADMIN` 分页查询。

### Redis 缓存模块

缓存用户详情和工单详情：

- 用户详情 key：`user:detail:{id}`，TTL 30 分钟。
- 工单详情 key：`ticket:detail:{id}`，TTL 10 分钟。

更新或删除用户后删除用户缓存。修改工单、修改状态、删除工单、新增回复后删除工单详情缓存。

### 统一响应与异常处理

所有接口返回统一 `Result` 格式。业务异常使用 `BusinessException`，由 `GlobalExceptionHandler` 统一转换为 JSON 响应。

### AI 基础通信与扩展预留

当前项目已提供基础 AI 通信入口：

```text
POST /ai/chat
```

Java 后端通过 `RestTemplate` 调用 Python FastAPI AI 服务：

```text
POST /agent/chat
```

AI 服务地址配置：

```properties
ai.service.base-url=http://127.0.0.1:8001
```

当前阶段只负责打通 Java 到 Python 的 HTTP 调用，不操作工单、回复或用户数据。后续可以增加：

- AI 工单分类
- AI 回复建议
- AI 工单总结
- AI 知识库问答
- Java 后端调用 Python FastAPI AI 服务
- AI 回复建议保存为 `TicketReply`，`replyType = AI`

## 项目结构

```text
src/main/java/com/example/hello_demo
├── HelloDemoApplication.java
├── common
│   ├── PageResult.java
│   └── Result.java
├── config
│   ├── MyBatisPlusConfig.java
│   ├── PasswordEncoderConfig.java
│   ├── RedisConfig.java
│   └── WebConfig.java
├── constant
│   └── RedisKeyConstants.java
├── controller
│   ├── AuthController.java
│   ├── HelloController.java
│   ├── OperationLogController.java
│   ├── TicketController.java
│   ├── TicketReplyController.java
│   └── UserController.java
├── dto
│   ├── LoginRequestDTO.java
│   ├── RegisterRequestDTO.java
│   ├── TicketCreateDTO.java
│   ├── TicketQueryRequest.java
│   ├── TicketReplyCreateDTO.java
│   ├── TicketStatusUpdateDTO.java
│   └── TicketUpdateRequest.java
├── entity
│   ├── OperationLog.java
│   ├── Ticket.java
│   ├── TicketReply.java
│   └── User.java
├── enums
│   ├── BusinessType.java
│   ├── OperationType.java
│   ├── TicketReplyType.java
│   ├── TicketStatus.java
│   └── UserRole.java
├── exception
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── mapper
│   ├── OperationLogMapper.java
│   ├── TicketMapper.java
│   ├── TicketReplyMapper.java
│   └── UserMapper.java
├── security
│   ├── CurrentUserContext.java
│   ├── JwtInterceptor.java
│   ├── JwtUtil.java
│   └── PermissionUtil.java
├── service
│   ├── AuthService.java
│   ├── OperationLogService.java
│   ├── RedisCacheService.java
│   ├── TicketReplyService.java
│   ├── TicketService.java
│   └── UserService.java
└── vo
    ├── CurrentUserVO.java
    ├── LoginResponseVO.java
    ├── TicketDetailVO.java
    └── UserInfoVO.java

src/main/resources
├── application.properties
└── sql
    ├── operation_log.sql
    ├── operation_log_upgrade.sql
    ├── schema_upgrade.sql
    ├── ticket.sql
    ├── ticket_reply.sql
    ├── user.sql
    └── user_auth_update.sql
```

## 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8+
- Redis 7+
- Postman
- Docker，可选，用于启动 Redis

## 数据库初始化

数据库名称：

```text
springboot_demo
```

数据库连接配置位置：

```text
src/main/resources/application.properties
```

当前配置示例：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/springboot_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=你的本机 MySQL 密码
```

SQL 文件目录：

```text
src/main/resources/sql
```

新库推荐执行顺序：

```text
1. user.sql
2. ticket.sql
3. ticket_reply.sql
4. operation_log.sql
```

已有旧库升级时，按实际缺失内容选择执行：

```text
user_auth_update.sql
schema_upgrade.sql
operation_log_upgrade.sql
```

执行升级 SQL 前，先用 `SHOW COLUMNS` 和 `SHOW INDEX` 检查字段、索引是否已存在，避免重复执行报错。

创建测试账号可以使用注册接口，也可以直接在数据库中修改角色。角色设置示例：

```sql
UPDATE user SET role = 'USER' WHERE username = 'user01';
UPDATE user SET role = 'STAFF' WHERE username = 'staff01';
UPDATE user SET role = 'ADMIN' WHERE username = 'admin01';
```

## Redis 启动方式

Docker 启动 Redis：

```bash
docker run -d --name redis-study -p 6379:6379 redis:7
```

如果容器已存在：

```bash
docker start redis-study
```

测试 Redis：

```bash
docker exec -it redis-study redis-cli
ping
```

返回：

```text
PONG
```

说明 Redis 正常。

WSL 方案：

```bash
sudo apt update
sudo apt install redis-server -y
sudo service redis-server start
redis-cli ping
```

Redis 配置位置：

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
spring.data.redis.timeout=3000ms
```

## 启动后端

先编译：

```bash
mvn clean compile
```

启动：

```bash
mvn spring-boot:run
```

也可以打包后启动：

```bash
mvn clean package
java -jar target/hello-demo-0.0.1-SNAPSHOT.jar
```

后端默认地址：

```text
http://localhost:8080
```

健康测试接口：

```http
GET http://localhost:8080/hello
```

## 接口测试方式

使用 Postman 导入项目根目录的：

```text
postman_collection.json
```

建议测试顺序：

1. 注册 USER / STAFF / ADMIN 三个账号。
2. 在数据库中把角色分别改成 `USER`、`STAFF`、`ADMIN`。
3. 登录三个账号，分别复制 token。
4. 在 Postman Collection Variables 中设置 `USER_TOKEN`、`STAFF_TOKEN`、`ADMIN_TOKEN`。
5. 后续接口在 Header 中携带：

```http
Authorization: Bearer <token>
```

详细测试步骤见：

```text
docs/postman-test-guide.md
```

接口文档见：

```text
docs/api.md
```

## 核心请求流程摘要

```text
客户端 / Postman 发送请求
↓
JwtInterceptor 校验 JWT Token
↓
CurrentUserContext 保存当前用户信息
↓
Controller 接收请求
↓
Service 处理业务、权限和缓存逻辑
↓
Mapper 操作 MySQL
↓
OperationLogService 记录关键操作
↓
RedisCacheService 读取或清除缓存
↓
Result 返回统一 JSON 响应
```

详细请求流程见：

```text
docs/request-flow.md
```

## 文档目录

- `docs/api.md`：接口文档。
- `docs/database.md`：数据库表结构、关系和索引说明。
- `docs/request-flow.md`：登录、JWT、工单、回复、权限等请求流程。
- `docs/learning-notes.md`：适合初学者复盘和面试讲解的学习笔记。
- `docs/postman-test-guide.md`：Postman 导入、变量配置和测试顺序。
- `docs/*-explained.md`：分主题解释文档。

## 常见问题

### MySQL 连接失败

检查 MySQL 是否启动、数据库 `springboot_demo` 是否存在、账号密码是否正确，以及 `application.properties` 中的连接配置是否匹配本机环境。

### Redis 连接失败

检查 Redis 是否启动、端口是否为 `6379`、Docker 容器是否运行，以及 `spring.data.redis.*` 配置是否正确。

### Docker 拉不到 Redis 镜像

检查网络或 Docker 镜像源。也可以改用 WSL 安装 `redis-server`。

### 端口 8080 被占用

修改 `application.properties`：

```properties
server.port=8081
```

或者停止占用 8080 的进程。

### 401 未登录

请求没有携带 Token，或者 Header 格式不正确。正确格式：

```http
Authorization: Bearer <token>
```

### 403 无权限

当前用户角色没有权限访问接口或数据。例如 `USER` 不能查询别人的工单，不能删除工单，不能查询操作日志。

### Maven 依赖下载失败

检查网络后重新执行：

```bash
mvn clean compile
```

### JWT Token 过期或错误

重新登录获取新的 Token，并确认 Postman 中对应变量已经更新。

### SQL 字段和 Entity 不一致

如果出现 `Unknown column`，说明数据库表结构和实体类字段不一致。检查 `src/main/resources/sql` 中的建表脚本和升级脚本，必要时补字段或索引。

## 后续 AI 扩展计划

当前版本已实现 Java 后端调用 Python AI 服务的基础通信能力，复杂 AI 业务仍作为后续扩展方向。

AI 服务地址配置：

```properties
ai.service.base-url=http://127.0.0.1:8001
```

Java AI 测试接口：

```http
POST /ai/chat
```

Python AI 服务接口：

```http
POST /agent/chat
```

后续可以新增：

```text
POST /ai/tickets/{id}/reply-suggestion
POST /ai/tickets/{id}/summary
POST /ai/tickets/{id}/classify
POST /ai/knowledge/ask
```

推荐架构：

```text
Java Spring Boot 后端
↓ HTTP 调用
Python FastAPI AI 服务
↓
大模型 / 向量数据库 / 知识库
```

AI 回复建议可以保存为 `TicketReply`：

```text
replyType = AI
```

这样不破坏现有工单回复模型，也方便后续在同一条工单时间线中展示用户、客服和 AI 的回复。

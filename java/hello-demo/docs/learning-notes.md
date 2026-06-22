# 学习笔记与面试讲解

本文档面向初学者，用来复盘这个 Spring Boot 后端项目学到的核心知识，并整理面试时可以怎么讲。

## 1. Spring Boot 请求流程

学到了什么：HTTP 请求进入后端后，会经过 Tomcat、DispatcherServlet、Controller、Service、Mapper，最后返回 JSON。

为什么需要：理解请求从哪里进、在哪里处理、在哪里返回，是学习后端的基础。

当前项目中哪里用到了：所有接口，例如 `POST /tickets`、`GET /tickets/{id}/detail`。

面试可以怎么说：我能讲清楚一个请求从 Postman 发出后，如何经过 JWT 拦截器、Controller、Service、Mapper，再通过统一响应返回。

## 2. Controller / Service / Mapper 分层

学到了什么：

- Controller 接收请求和返回响应。
- Service 处理业务规则、权限、缓存和日志。
- Mapper 访问数据库。

为什么需要：分层可以让代码职责清楚，避免 Controller 里堆满业务判断。

当前项目中哪里用到了：`TicketController` 只接收请求，状态流转规则放在 `TicketService`，数据库操作由 `TicketMapper` 完成。

面试可以怎么说：我把业务规则放在 Service 层，这样接口层更薄，也更容易测试和维护。

## 3. Result 统一响应

学到了什么：接口统一返回 `code`、`message`、`data`。

为什么需要：前端或 Postman 能用同一种方式判断成功和失败。

当前项目中哪里用到了：`common/Result.java`，所有 Controller 都返回 `Result<T>`。

面试可以怎么说：我用统一响应结构降低前后端联调成本，让异常和成功响应格式一致。

## 4. BusinessException 和 GlobalExceptionHandler

学到了什么：业务错误用 `BusinessException` 抛出，全局异常处理器统一转换成 JSON。

为什么需要：避免每个 Controller 都写 try-catch。

当前项目中哪里用到了：用户不存在、工单不存在、无权限、状态不合法等。

面试可以怎么说：我把可预期业务错误封装成 BusinessException，再由全局异常处理返回统一错误格式。

## 5. Validation 参数校验

学到了什么：使用 `@Valid`、`@NotBlank`、`@Email` 等注解校验请求参数。

为什么需要：参数错误要尽早拦截，避免脏数据进入业务逻辑。

当前项目中哪里用到了：`LoginRequestDTO`、`RegisterRequestDTO`、`TicketCreateDTO`、`TicketReplyCreateDTO`。

面试可以怎么说：我用 DTO 接收请求，并通过 Validation 注解做基础校验，业务校验继续放在 Service 层。

## 6. MyBatis-Plus CRUD

学到了什么：Mapper 继承 `BaseMapper<T>` 后，可以直接使用 `selectById`、`insert`、`updateById`、`deleteById` 等方法。

为什么需要：减少重复 SQL，让基础 CRUD 更简单。

当前项目中哪里用到了：`UserMapper`、`TicketMapper`、`TicketReplyMapper`、`OperationLogMapper`。

面试可以怎么说：项目使用 MyBatis-Plus 提供基础 CRUD，同时用 `LambdaQueryWrapper` 处理条件查询。

## 7. 条件查询和分页

学到了什么：分页用 `Page<T>`，条件查询用 `LambdaQueryWrapper`。

为什么需要：真实项目不能一次查询所有数据，分页可以保护数据库和接口性能。

当前项目中哪里用到了：`GET /tickets` 和 `GET /operation-logs`。

面试可以怎么说：我限制了 page、size，size 最大 100，并支持 status、priority、category、keyword 等条件查询。

## 8. 状态流转

学到了什么：工单状态不能随便改，需要有流转规则。

为什么需要：业务流程要可控，不能让已关闭工单重新变成处理中。

当前项目中哪里用到了：`TicketService.updateTicketStatus`。

面试可以怎么说：我定义了 `OPEN -> PROCESSING/CLOSED`、`PROCESSING -> CLOSED`，并禁止 `CLOSED` 再修改。

## 9. 一对多表关系

学到了什么：一个用户可以有多个工单，一个工单可以有多条回复。

为什么需要：数据要拆成合理的表，避免把所有回复都堆到工单表里。

当前项目中哪里用到了：`ticket.user_id`、`ticket_reply.ticket_id`、`ticket_reply.user_id`。

面试可以怎么说：我用 user、ticket、ticket_reply 三张表表达用户、工单、回复之间的一对多关系。

## 10. JWT 登录认证

学到了什么：登录成功后生成 Token，后续请求通过 Header 携带 Token。

为什么需要：后端需要知道当前请求是谁发起的。

当前项目中哪里用到了：`JwtUtil`、`JwtInterceptor`、`CurrentUserContext`。

面试可以怎么说：我通过拦截器解析 JWT，把 userId、username、role 放入 ThreadLocal，Service 从上下文读取当前用户。

## 11. 权限控制

学到了什么：权限分为接口权限和数据权限。

为什么需要：普通用户不能查看别人的工单，不能删除工单，不能查询操作日志。

当前项目中哪里用到了：`PermissionUtil`、`UserRole`、`TicketService`、`UserService`。

面试可以怎么说：我在 Service 层做权限判断，USER 只能操作自己的数据，STAFF 可以处理工单，ADMIN 负责管理和审计。

## 12. 操作日志

学到了什么：系统要记录关键操作，方便审计和排查。

为什么需要：真实项目要知道是谁在什么时候做了什么。

当前项目中哪里用到了：注册、登录成功、登录失败、创建工单、回复工单、修改状态、删除工单。

面试可以怎么说：我实现了 operation_log 表和 OperationLogService，第一版没有用 AOP，而是在关键 Service 成功路径直接记录。

## 13. Redis 缓存

学到了什么：Redis 用来缓存读多写少的数据，减少 MySQL 压力。

为什么需要：用户详情和工单详情经常查询，适合缓存。

当前项目中哪里用到了：`GET /users/{id}` 缓存 `user:detail:{id}`，`GET /tickets/{id}/detail` 缓存 `ticket:detail:{id}`。

面试可以怎么说：我采用 Cache Aside 模式，查询先查 Redis，未命中查 MySQL 并写缓存，修改或删除后删除缓存。

## 14. Postman 接口测试

学到了什么：用 Postman 可以完整测试登录、Token、权限、CRUD、分页、缓存和操作日志。

为什么需要：没有前端时，Postman 是验证后端接口的主要工具。

当前项目中哪里用到了：项目根目录提供 `postman_collection.json`，并有 `docs/postman-test-guide.md`。

面试可以怎么说：我整理了 Postman Collection，通过变量管理 baseUrl、token、ticketId、userId，可以按流程跑通主要接口。

## 15. 后续 AI 如何接入

学到了什么：AI 功能可以作为独立服务接入，不必混在当前后端里。

为什么需要：Java 后端负责业务和权限，Python FastAPI AI 服务负责大模型、向量库、知识库问答。

当前项目中哪里预留了：`TicketReply.replyType` 已支持 `AI`，文档中预留 AI 工单分类、回复建议、总结、知识库问答接口。

面试可以怎么说：我会让 Spring Boot 调用 Python FastAPI AI 服务，AI 建议结果可以保存为 TicketReply，replyType 设置为 AI，从而复用现有工单回复模型。

## 16. 服务间 HTTP 调用

学到了什么：Java 后端可以通过 `RestTemplate` 同步调用 Python FastAPI 服务。

为什么需要：前端和 Postman 统一访问 Java 后端，由 Java 负责登录认证、权限控制和统一响应，Python 只负责 AI / Agent 能力。

当前项目中哪里用到了：`POST /ai/chat` 调用 Python `POST /agent/chat`。

关键配置：

```properties
ai.service.base-url=http://127.0.0.1:8001
```

关键分层：

```text
AiController -> AiService -> AiClient -> Python /agent/chat
```

学到的设计点：

- Python 服务地址放在配置文件中，不硬编码到业务代码。
- `RestTemplateConfig` 设置连接超时和读取超时，避免 Python 服务异常时 Java 请求一直等待。
- Python 服务不可用时，`AiClient` 抛出 `BusinessException`，再由 `GlobalExceptionHandler` 返回统一错误格式。
- 当前阶段只做基础通信，不做 pending_action、RAG、真实大模型编排，也不操作工单数据库。

面试可以怎么说：我把 Java 后端作为统一入口，先校验 JWT，再由 Service 调 Client 访问 Python AI 服务。这样 AI 能力可以独立演进，同时不绕过已有权限和业务边界。

## 17. Java 调用 Python AI 服务复盘问答

### 问题 1：这次新增了什么功能？

回答：新增了 Java 后端接口 `POST /ai/chat`。它接收用户传入的 `message`，再通过 HTTP 调用 Python AI 服务 `POST /agent/chat`。Python 返回什么，Java 就把它放进统一 `Result` 的 `data` 中返回。

请求链路：

```text
前端 / Postman
↓
Java POST /ai/chat
↓
Python POST /agent/chat
↓
Java Result 包装返回
```

### 问题 2：为什么不让前端直接调用 Python？

回答：因为 Java 后端已经负责登录认证、JWT、权限控制、统一响应和统一异常处理。如果前端直接调用 Python，就会绕过 Java 的安全边界。

更合理的结构是：

```text
前端 / Postman 只访问 Java
Java 负责认证、权限、统一入口
Python 只负责 AI / Agent 能力
```

### 问题 3：为什么要分成 `AiController`、`AiService`、`AiClient` 三层？

回答：这是为了职责分离。

- `AiController` 只负责接收请求和返回 `Result`。
- `AiService` 作为 AI 业务服务层，当前只做转发，后续可以加入权限、日志、工单上下文、AI 编排。
- `AiClient` 专门负责通过 HTTP 调用 Python 服务。

这样 Controller 不直接写 HTTP 调用逻辑，后续扩展时也不会把业务逻辑堆在 Controller 里。

### 问题 4：`AiClient` 主要学什么？

回答：主要学习 Java 后端如何调用外部 HTTP 服务。

当前实现使用 `RestTemplate.exchange(...)`：

```text
AiClient.chat(...)
↓
组装 Python /agent/chat 地址
↓
设置 Content-Type: application/json
↓
发送 AiChatRequestDTO
↓
用 Map<String, Object> 接收 Python JSON
```

### 问题 5：为什么 Python 返回值先用 `Map<String, Object>` 接收？

回答：因为当前阶段只要求打通基础通信，Python 返回结构还可能变化。使用 `Map<String, Object>` 可以先接住任意 JSON，避免过早设计固定 VO。

后续如果 Python 响应结构稳定，比如固定返回：

```json
{
  "answer": "..."
}
```

再改成固定 VO 会更合适。

### 问题 6：为什么 Python 服务地址要写在配置文件？

回答：服务地址不应该硬编码在 Java 代码里。当前配置在 `application.properties`：

```properties
ai.service.base-url=http://127.0.0.1:8001
```

这样以后 Python 服务部署到别的机器或端口时，只需要改配置，不需要改代码。

### 问题 7：`RestTemplateConfig` 的作用是什么？

回答：`RestTemplateConfig` 把 `RestTemplate` 注册成 Spring Bean，供 `AiClient` 注入使用。

它还设置了超时时间：

```text
连接超时：3 秒
读取超时：10 秒
```

这样 Python 服务不可用或响应太慢时，Java 请求不会一直卡住。

### 问题 8：Python 服务不可用时如何处理？

回答：`AiClient` 捕获 `ResourceAccessException`，再抛出业务异常：

```text
AI服务连接超时或不可用
```

然后由 `GlobalExceptionHandler` 转成统一 `Result`：

```json
{
  "code": 500,
  "message": "AI服务连接超时或不可用",
  "data": null
}
```

### 问题 9：`/ai/chat` 是否需要登录？

回答：需要。当前没有把 `/ai/chat` 加入 JWT 白名单，所以它会继续走已有的 `JwtInterceptor`。

不带 Token 时会返回：

```json
{
  "code": 401,
  "message": "请先登录",
  "data": null
}
```

这说明 AI 入口仍然受 Java 后端认证保护。

### 问题 10：空 `message` 为什么会返回参数错误？

回答：因为 `AiChatRequestDTO` 使用了 Validation 注解：

```java
@NotBlank(message = "message不能为空")
private String message;
```

Controller 中使用 `@Valid`，所以空字符串会在进入业务逻辑前被拦截，并由全局异常处理器返回统一错误。

### 问题 11：这次为什么不做工单查询、pending_action、RAG 或真实大模型？

回答：因为本阶段目标只有一个：验证 Java 能否成功调用 Python AI 服务。

本次明确不做：

- 不操作 Ticket 数据库。
- 不操作 TicketReply 数据库。
- 不操作 User 数据库。
- 不实现 pending_action。
- 不实现 RAG。
- 不实现真实大模型编排。
- 不保存 AI 回复。
- 不新增前端。

这样可以先把服务间通信链路打稳，再逐步扩展复杂能力。

### 问题 12：如何测试 Python `/agent/chat`？

回答：先启动 Python 服务：

```powershell
uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

再用 Postman 请求：

```http
POST http://127.0.0.1:8001/agent/chat
Content-Type: application/json
```

请求体：

```json
{
  "message": "你能做什么"
}
```

### 问题 13：如何测试 Java `/ai/chat`？

回答：先启动 Java 后端：

```powershell
mvn spring-boot:run
```

再用 Postman 请求：

```http
POST http://localhost:8080/ai/chat
Authorization: Bearer 你的JWT_TOKEN
Content-Type: application/json
```

请求体：

```json
{
  "message": "你能做什么"
}
```

预期返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "Python AI 服务返回的内容"
  }
}
```

### 问题 14：这次学习的核心结论是什么？

回答：Java 后端应该作为系统统一入口，先完成 JWT 认证、权限边界和统一响应，再通过配置化的 HTTP Client 调用 Python AI 服务。这样既能接入 AI 能力，又不会破坏原有工单系统的安全和业务边界。

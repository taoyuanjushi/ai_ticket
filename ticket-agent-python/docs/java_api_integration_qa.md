# Python Agent 接入 Java API 学习复盘问答

本文用初学者能理解的方式，解释为什么要把 Python Agent 的工单工具从本地 mock 数据切换到 Java Spring Boot REST API。

当前阶段的核心链路是：

```text
用户 / Postman
↓
Python /agent/chat
↓
Python Agent 判断意图
↓
JavaTicketClient 调用 Java REST API
↓
Java 后端处理权限、业务规则、数据库
↓
Python 把结果整理成自然语言回答
```

## 1. mock 数据和真实 Java API 数据源有什么区别？

mock 数据是假的本地测试数据，通常写在 Python 代码或内存列表里，比如：

```python
MOCK_TICKETS = [
    {"id": 1, "title": "登录失败", "status": "OPEN"}
]
```

它适合学习和早期测试，因为不用启动数据库，也不用启动 Java 后端。

真实 Java API 数据源是指 Python 不再使用自己的假数据，而是通过 HTTP 请求访问 Java 后端，例如：

```text
GET http://127.0.0.1:8080/tickets
```

区别可以这样理解：

```text
mock 数据：练习用的小样本，服务重启后可能丢失，不代表真实系统。
Java API 数据：真实业务数据，存储在 Java 后端管理的数据库中，受权限和业务规则保护。
```

所以第二阶段切换到 Java API 后，Python Agent 查到和修改的就是 Java 工单系统里的真实数据。

## 2. 为什么 Python Agent 不应该直接操作 MySQL？

因为当前系统里，Java 后端才是工单业务的主人。

Java 后端已经负责：

- 用户登录和 JWT 校验。
- USER / STAFF / ADMIN 权限控制。
- 工单创建、查询、修改状态等业务规则。
- Redis 缓存清理。
- 操作日志记录。
- 统一响应和统一异常处理。

如果 Python 直接连 MySQL，就会绕过这些规则。例如 Python 可能直接把一个已关闭工单改成处理中，Java 的状态流转规则就失效了。

正确做法是：

```text
Python 不碰数据库
↓
Python 调 Java API
↓
Java 决定能不能查、能不能改、怎么写数据库
```

这样系统边界更清晰，也更安全。

## 3. 为什么 Python 工具应该通过 Java REST API 操作工单？

因为 Java REST API 是工单系统对外提供的正式入口。

Python 工具通过 Java API 操作工单，有几个好处：

- 不绕过登录和权限。
- 不重复写一套工单业务规则。
- 不需要 Python 理解 MySQL 表结构。
- Java 后端未来改数据库表结构时，Python 影响更小。
- Python 只专注 Agent、自然语言理解、工具编排。

比如创建工单时，Python 只需要调用：

```text
POST /tickets
```

至于 `userId` 从哪里来、默认状态是什么、是否记录日志，都由 Java 后端处理。

## 4. JavaTicketClient 在架构中负责什么？

`JavaTicketClient` 是 Python 项目里专门调用 Java 后端的客户端。

它的职责很单一：

```text
把 Python 工具请求
转换成
Java REST API 请求
```

当前它主要负责三个方法：

```text
search_tickets -> GET /tickets
create_ticket -> POST /tickets
update_ticket_status -> PUT /tickets/{id}/status
```

它不负责判断用户意图，也不负责确认流程，更不负责写数据库。

简单说：

```text
AgentToolService 负责“什么时候调用工具”
ticket_tools.py 负责“工具函数叫什么、接收什么参数”
JavaTicketClient 负责“怎么请求 Java”
Java Service 负责“真实业务规则和数据库”
```

## 5. JAVA_API_BASE_URL 为什么要放到环境变量，而不是写死在代码里？

因为服务地址在不同环境中可能不一样。

本地开发时可能是：

```text
http://127.0.0.1:8080
```

测试环境可能是：

```text
http://test-java-api:8080
```

生产环境可能是：

```text
https://api.example.com
```

如果把地址写死在代码里，每换一个环境就要改代码、重新发布。

放到 `.env` 或环境变量中：

```env
JAVA_API_BASE_URL=http://127.0.0.1:8080
```

以后只改配置，不改代码。

## 6. Python 调 Java 接口时为什么需要携带 JWT Token？

因为 Java 后端的大部分接口都需要登录。

Java 通过请求头里的 Token 判断：

```text
你是谁
你是什么角色
你有没有权限访问这个接口
你能不能操作这条工单
```

请求头格式是：

```http
Authorization: Bearer <JWT_TOKEN>
```

如果 Python 不带 Token，Java 会认为请求没有登录，通常返回：

```json
{
  "code": 401,
  "message": "请先登录",
  "data": null
}
```

所以 `/agent/chat` 支持传入：

```json
{
  "message": "查一下所有工单",
  "auth_token": "从 Java 登录接口拿到的 token"
}
```

Python 工具再把这个 `auth_token` 交给 `JavaTicketClient`，由它带着 Token 请求 Java。

## 7. search_tickets 为什么可以直接执行？

因为 `search_tickets` 是查询操作，只读数据，不改变数据库。

它做的是：

```text
看一看工单列表
查一查某个状态的工单
按关键词搜索工单
```

这类操作通常风险较低，所以用户说“查一下处理中工单”时，可以直接调用 Java：

```text
GET /tickets?status=PROCESSING
```

但是直接执行不等于没有权限。

即使 Python 直接调用，Java 后端仍然会根据 JWT 判断当前用户能看到哪些工单。

## 8. create_ticket 和 update_ticket_status 为什么仍然需要 Human-in-the-loop？

因为它们是写操作，会改变真实数据。

`create_ticket` 会新增工单：

```text
POST /tickets
```

`update_ticket_status` 会修改工单状态：

```text
PUT /tickets/{id}/status
```

如果 Agent 第一次听到用户说“把 3 号工单改成关闭”就直接执行，风险很高。用户可能说错 ID，Agent 也可能理解错意图。

所以需要 Human-in-the-loop：

```text
用户提出写操作
↓
Agent 先整理参数
↓
返回确认提示
↓
用户回复“确认”
↓
才真正调用 Java API
```

这样能避免误创建、误修改。

## 9. Python tool 和 Java Service 的职责边界是什么？

可以这样分工：

```text
Python tool：负责把 Agent 的工具调用转换成 Java API 请求。
Java Service：负责真正的业务规则、权限判断和数据库写入。
```

Python tool 不应该做这些事：

- 不直接连接 MySQL。
- 不判断复杂权限。
- 不维护真实工单状态。
- 不重复实现 Java 的业务规则。

Java Service 应该负责这些事：

- 当前用户能不能查这条工单。
- STAFF / ADMIN 能不能修改状态。
- 工单状态能不能流转。
- 数据写入 MySQL。
- 清理缓存。
- 记录操作日志。

一句话：

```text
Python 负责“智能入口和工具编排”
Java 负责“真实业务和数据一致性”
```

## 10. Java API 调用失败时，Python Agent 应该怎么处理？

Python Agent 不应该把技术堆栈直接暴露给用户。

错误处理应该做到：

- Java 没启动：告诉用户 Java 后端不可用。
- 没带 Token：告诉用户需要检查 `auth_token` 或 `JAVA_API_TOKEN`。
- 权限不足：告诉用户当前账号没有权限。
- 工单不存在：告诉用户 Java 返回工单不存在。
- 参数错误：尽量返回 Java 的业务错误信息。

例如 Java 没启动时，Python 可以返回：

```text
无法连接 Java API，请确认 Java 后端是否已启动：http://127.0.0.1:8080
```

Java 返回未登录时：

```text
Java API 返回未登录，请检查 auth_token 或 JAVA_API_TOKEN
```

这样用户能知道下一步该检查什么，而不是看到一堆难懂的异常堆栈。

## 11. Java 返回 Result 格式后，Python 应该如何取出 data？

Java 后端统一返回类似这样的结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": []
  }
}
```

Python 要先判断 `code`：

```text
code == 200：说明业务成功，取出 data。
code != 200：说明业务失败，读取 message 并返回友好错误。
```

也就是说，Python 不应该直接把整个 Result 当作工单数据。

正确理解是：

```text
Result 是外包装
data 才是真正的数据
message 是错误或成功说明
code 是业务状态码
```

## 12. 为什么这一阶段还不应该让 Python 直接写 pending_action？

这里要区分两个概念：

```text
pending_action 本身：等待用户确认的动作计划。
真实业务数据：工单、回复、用户等数据库数据。
```

当前 Python 可以在内存中暂存一个简单的 `pending_action`，用于学习确认流程。但这一阶段不应该把 Python 设计成复杂的 pending_action 持久化系统。

原因是：

- 当前重点是工具从 mock 数据切到 Java API。
- 持久化 pending_action 会引入新的表设计、过期时间、安全问题。
- 如果把 Token 或敏感上下文长期存在 Python，需要额外安全设计。
- 多用户、多 worker 场景下，内存 pending_action 不可靠。

所以当前更合理的是：

```text
Python 只保留简单内存 pending_action
真实业务数据仍由 Java 管理
后续需要生产化时，再考虑把待确认动作交给 Java 或 LangGraph 状态管理
```

## 13. 如果 Java 接口路径变化，为什么只改 JavaTicketClient 就够了？

因为工具函数不直接写 Java URL。

正确结构是：

```text
search_tickets 工具
↓
JavaTicketClient.search_tickets()
↓
GET /tickets
```

如果以后 Java 把路径从：

```text
GET /tickets
```

改成：

```text
GET /api/tickets
```

只需要改 `JavaTicketClient` 里拼 URL 的地方。

Agent 逻辑、工具函数名、确认流程都不用大改。

这就是封装的价值：

```text
变化集中在一个地方
其他代码不用知道 Java 接口细节
```

## 14. 服务间 HTTP 调用和本地函数调用有什么区别？

本地函数调用是在同一个进程里调用代码，例如：

```python
result = search_tickets()
```

它通常速度快，失败原因也比较直接。

服务间 HTTP 调用是一个服务通过网络请求另一个服务，例如：

```text
Python -> HTTP -> Java
```

它会多出一些问题：

- Java 服务可能没启动。
- 网络可能不通。
- 请求可能超时。
- Token 可能过期。
- Java 可能返回 401 / 403 / 404。
- 返回内容可能不是预期 JSON。

所以服务间调用必须考虑：

```text
超时
错误处理
认证信息
响应格式解析
日志排查
```

这也是为什么要单独写 `JavaTicketClient`，而不是在每个工具里随手写 HTTP 请求。

## 15. 这个阶段如何证明 AI 服务已经真正接入 Java 后端？

可以从几个方面证明。

第一，配置里已经有 Java 地址：

```env
JAVA_API_BASE_URL=http://127.0.0.1:8080
```

第二，Python 工具函数不再读写本地 `MOCK_TICKETS`，而是调用 `JavaTicketClient`。

第三，查询工单时，如果 Java 没启动，Python 会返回 Java 不可用，而不是继续返回本地假数据。

第四，用 Postman 测试：

```json
{
  "message": "查一下所有处理中工单",
  "auth_token": "Java 登录得到的 token"
}
```

如果返回的是 Java 后端真实工单，就说明查询已经接入 Java。

第五，测试创建工单：

```text
先让 Agent 返回确认
再发送“确认”
然后去 Java /tickets 查询
能查到新工单
```

第六，测试修改状态：

```text
先让 Agent 返回确认
再发送“确认”
然后去 Java /tickets/{id} 查询
状态已经改变
```

这几个现象一起出现，就能说明：

```text
Python Agent 不再只是本地 mock 演示
而是已经通过 Java REST API 接入真实工单后端
```

## 总结

这一阶段最重要的学习点是：

```text
Python Agent 负责理解用户意图和编排工具。
Java 后端负责真实业务、权限和数据库。
两者通过 JavaTicketClient + REST API 连接。
查询直接执行，写操作必须确认。
```

这样既能保留 AI Agent 的灵活性，又不会破坏原有工单系统的安全边界和业务规则。

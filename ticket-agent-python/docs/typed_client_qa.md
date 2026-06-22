# Typed Client 学习复盘问答

本文用初学者能理解的方式，解释为什么本阶段要把 Python AI 服务里的 `JavaTicketClient` 改成 typed client，以及 DTO、Pydantic、Client 层统一错误处理分别解决什么问题。

当前阶段的核心变化是：

```text
以前：
JavaTicketClient 返回原始 dict
↓
工具层自己猜字段、取 data、取 records、处理错误

现在：
JavaTicketClient 返回 TicketDTO / TicketDetailDTO 等强类型对象
↓
工具层只关心业务含义，不关心 Java 原始响应格式
```

## 1. 什么是 typed client？

`typed client` 可以理解为“带类型说明的接口客户端”。

在当前项目里，`JavaTicketClient` 负责从 Python 调 Java 后端：

```text
Python Agent
↓
JavaTicketClient
↓
Java REST API
```

以前它可能直接返回这样的普通 `dict`：

```python
{
    "id": 1,
    "title": "登录失败",
    "status": "OPEN"
}
```

这种数据虽然能用，但代码不知道它一定有什么字段，也不知道字段类型对不对。

改成 typed client 后，它返回的是明确的对象，例如：

```python
TicketDTO(
    id=1,
    title="登录失败",
    status=TicketStatus.OPEN,
)
```

这样代码一看就知道：

```text
这是一个工单
它有 id
它有 title
它的 status 必须是 OPEN / PROCESSING / CLOSED
```

所以 typed client 的作用是：

```text
把外部接口返回的不确定数据
转换成 Python 内部更清楚、更稳定的对象
```

## 2. 为什么不建议业务代码到处处理 dict？

`dict` 很灵活，但太灵活也会带来问题。

比如 Java 返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "title": "登录失败",
        "content": "用户无法登录",
        "status": "OPEN"
      }
    ]
  }
}
```

如果业务代码到处直接处理 `dict`，就会出现很多重复代码：

```python
data = response["data"]
records = data["records"]
title = records[0]["title"]
status = records[0]["status"]
```

问题是：

- 每个地方都要记住 Java 的返回格式。
- 字段名写错时，运行时才会报错。
- Java 字段变化时，要全项目搜索修改。
- 错误处理会散落在很多地方。
- 新人很难看出这段 dict 到底表示什么业务对象。

更好的方式是：

```text
Java 原始响应只在 JavaTicketClient 里解析一次
↓
转换成 TicketDTO / TicketDetailDTO
↓
其他业务代码使用 DTO
```

这样工具层就不用知道 Java 外层 `code/message/data`，也不用知道分页字段叫 `records`。

## 3. DTO、Entity、VO、Request、Response 有什么区别？

这些名字都表示“数据对象”，但用途不同。

可以用餐厅点餐来类比：

```text
Request：客人点菜单
Entity：厨房真实库存记录
DTO：服务员端给厨房或前台传递的标准信息单
VO：展示给客人看的菜单或账单
Response：服务员最终端出来的答复
```

放到当前工单系统里：

| 名称 | 主要用途 | 当前项目例子 |
|---|---|---|
| Entity | 数据库表对应的对象 | Java 的 `Ticket`、`TicketReply` |
| Request | 前端或接口调用传入的数据 | `CreateTicketRequest`、`UpdateTicketStatusRequest` |
| Response | 接口返回给调用方的数据 | Java 的 `Result`、Python 的 `AgentChatResponse` |
| DTO | 服务之间传输的标准数据对象 | Python 的 `TicketDTO`、`TicketDetailDTO` |
| VO | 面向页面展示的数据对象 | Java 的 `TicketDetailVO`、`UserInfoVO` |

更简单地说：

```text
Entity：更靠近数据库
Request：请求进来时用
Response：响应出去时用
DTO：服务之间或层之间传递时用
VO：给页面展示时用
```

它们不是绝对不能混用，但在系统变复杂后，分清楚会更容易维护。

## 4. Pydantic 在 Python 后端中解决什么问题？

Pydantic 是 Python 里常用的数据校验和转换工具。

它能帮我们做几件事：

第一，检查字段是否存在：

```python
class TicketDTO(BaseModel):
    id: int
    title: str
```

如果 Java 返回里没有 `id` 或 `title`，Pydantic 会报错。

第二，检查类型是否正确：

```text
id 应该是 int
title 应该是 str
status 应该是固定枚举值
```

第三，自动做字段兼容。

比如 Java 返回的是：

```json
{
  "content": "用户无法登录",
  "userId": 7
}
```

Python 内部希望叫：

```python
description
createdBy
```

Pydantic 可以通过 alias 把它们兼容起来：

```text
content -> description
userId -> createdBy
```

第四，让代码更容易读。

看到 `TicketDTO`，就知道这是工单数据；看到 `dict`，还要猜里面有什么字段。

所以 Pydantic 的作用可以总结为：

```text
把不确定的外部 JSON
变成确定的 Python 对象
```

## 5. 为什么外部 API 错误要在 Client 层统一处理？

因为外部 API 的错误属于“调用外部服务时发生的问题”，最了解这些细节的是 Client。

当前系统里，Java API 可能返回：

```json
{
  "code": 401,
  "message": "Token无效或已过期",
  "data": null
}
```

也可能返回 HTTP 500、连接超时、非 JSON 响应。

如果每个工具都自己处理这些错误，就会变成：

```text
search_tickets 处理一遍 401
create_ticket 处理一遍 401
update_ticket_status 处理一遍 401
reply_suggestion 处理一遍 401
```

这样很容易不一致。

所以应该放在 `JavaTicketClient` 里统一处理：

```text
Java 返回 401 -> 登录状态已失效，请重新登录。
Java 返回 403 -> 你没有权限执行该操作。
Java 返回 404 -> 目标工单不存在，或你无权访问该工单。
Java 返回 400 -> 优先使用 Java 返回的 message。
Java 返回 500 -> 服务暂时异常，请稍后重试。
```

统一处理的好处是：

- 用户看到的错误提示一致。
- 工具层代码更简单。
- 后续要改错误文案，只改一个地方。
- 不会把 Java 的原始异常细节到处泄露。

简单说：

```text
Client 层负责“怎么调用外部服务、外部服务错了怎么办”
工具层负责“这个工具业务上要做什么”
```

## 6. 为什么工具层不应该直接写 requests？

工具层的职责是“提供 Agent 可以调用的工具”，比如：

```text
search_tickets：查询工单
create_ticket：创建工单
update_ticket_status：修改状态
```

它不应该关心太多 HTTP 细节，例如：

- Java 地址是什么。
- URL 怎么拼。
- Authorization 怎么带。
- Java Result 怎么拆。
- HTTP 401 / 403 / 404 怎么处理。
- Java 字段 `content` 怎么变成 Python 的 `description`。

如果工具层直接写 `requests` 或 `httpx`，代码会变成这样：

```python
response = requests.get(url, headers=headers)
payload = response.json()
if payload["code"] != 200:
    ...
records = payload["data"]["records"]
```

这会让工具层同时做两件事：

```text
既做业务工具
又做 HTTP 客户端
```

职责混在一起，后续就难维护。

更清晰的结构是：

```text
AgentToolService
负责判断什么时候调用工具

ticket_tools.py
负责定义工具入参和工具输出

JavaTicketClient
负责 HTTP 调用、Token、Java Result、错误处理、DTO 转换

Java 后端
负责权限、业务规则、数据库、缓存、操作日志
```

这样以后 Java 接口路径变化，只需要改 `JavaTicketClient`；工具层不用知道底层细节。

## 小结

本阶段 typed client 改造的核心价值是：

```text
把外部接口的不确定性挡在 Client 层
让业务代码拿到更清楚、更稳定的数据对象
```

也可以记成一句话：

```text
工具层不要到处拆 dict，也不要到处写 HTTP。
让 JavaTicketClient 统一调用 Java，统一解析响应，统一返回 DTO。
```

这样系统会更容易读、更容易测，也更不容易因为接口字段变化而到处坏。

# Ticket 工单状态流转核心概念说明

本文档解释当前项目中 `PUT /tickets/{id}/status` 状态流转接口背后的设计原因。适合配合以下代码一起阅读：

- `controller/TicketController.java`
- `service/TicketService.java`
- `dto/TicketStatusUpdateDTO.java`
- `enums/TicketStatus.java`
- `exception/BusinessException.java`
- `exception/GlobalExceptionHandler.java`

## 1. 什么是工单状态流转？

工单状态流转，就是工单从一个状态变成另一个状态的过程。

当前项目中，工单有 3 个核心状态：

| 状态 | 含义 |
| --- | --- |
| `OPEN` | 待处理 |
| `PROCESSING` | 处理中 |
| `CLOSED` | 已关闭 |

例如：

```text
OPEN -> PROCESSING -> CLOSED
```

表示一个工单先被创建出来，等待处理；然后有人开始处理；最后处理完成并关闭。

状态流转不是随便修改字符串，而是有业务规则限制。当前项目允许：

```text
OPEN -> PROCESSING
OPEN -> CLOSED
PROCESSING -> CLOSED
```

不允许：

```text
CLOSED -> OPEN
CLOSED -> PROCESSING
PROCESSING -> OPEN
OPEN -> OPEN
```

## 2. 为什么 CLOSED 工单不应该再修改状态？

`CLOSED` 表示工单已经结束。

真实业务里，关闭通常代表：

- 问题已经处理完；
- 处理结果已经确认；
- 后续统计会把它当作完成数据；
- 可能已经进入报表、绩效或审计流程。

如果关闭后的工单还能随意改回 `OPEN` 或 `PROCESSING`，会带来几个问题：

- 历史记录不可靠：已经关闭的数据又变成未关闭，统计会混乱。
- 责任边界不清楚：到底是谁在什么时候重新打开了问题，不容易追踪。
- 前端状态混乱：用户看到一个已关闭工单又被改回处理中，会不符合预期。
- 业务流程失控：关闭本来是流程终点，如果能随意跳出，规则就失去意义。

所以当前项目中，`TicketService` 明确判断：

```java
if (TicketStatus.CLOSED.name().equals(currentStatus)) {
    throw new BusinessException(400, "已关闭工单不允许修改状态");
}
```

## 3. 为什么状态流转规则应该写在 Service 层？

Service 层负责业务规则。

状态流转规则本质上是业务规则，不是 HTTP 请求规则，也不是数据库访问规则。

当前接口的规则包括：

- 工单是否存在；
- 新状态是否合法；
- 当前状态是否是 `CLOSED`；
- `OPEN` 能不能变成目标状态；
- `PROCESSING` 能不能变成目标状态；
- 更新数据库后返回最新工单。

这些都属于业务判断，应该放在 `TicketService`。

这样做有几个好处：

- Controller 保持简单，只负责接收请求和返回响应。
- Mapper 保持简单，只负责数据库操作。
- 状态规则集中在一个地方，后续修改规则更容易。
- 如果将来不仅 HTTP 接口要改状态，定时任务或后台管理也要改状态，也能复用同一个 Service 方法。

## 4. enum 和 String 表示状态有什么区别？

`String` 是普通字符串，任何值都能写进去。

例如：

```java
String status = "ABC";
String status = "hello";
String status = "处理中";
```

这些在 Java 语法上都没问题，但业务上可能完全不合法。

`enum` 是枚举，只允许预先定义好的固定值。

当前项目中：

```java
public enum TicketStatus {
    OPEN,
    PROCESSING,
    CLOSED;
}
```

它表达的是：工单状态只能是这 3 种之一。

对比：

| 方式 | 优点 | 风险 |
| --- | --- | --- |
| `String` | 简单、灵活 | 容易写入非法值，比如 `ABC` |
| `enum` | 状态范围清晰、便于校验 | 需要提前定义可用状态 |

当前项目数据库字段仍然是字符串，但代码中通过 `TicketStatus` 枚举统一校验，避免非法状态进入业务流程。

## 5. 为什么 PUT /tickets/{id}/status 只需要接收 status，而不是整个 Ticket？

这个接口的目标很明确：只修改工单状态。

所以请求体只需要：

```json
{
  "status": "PROCESSING"
}
```

如果直接接收整个 `Ticket`，前端可能传入很多不该修改的字段，例如：

```json
{
  "id": 99,
  "title": "恶意修改标题",
  "content": "恶意修改内容",
  "status": "PROCESSING",
  "createdAt": "2099-01-01T00:00:00"
}
```

这会带来风险：

- 本来只想改状态，却可能误改标题、内容、分类等字段。
- 前端传入 `id`，可能和路径里的 `{id}` 冲突。
- 接口含义变模糊，后续维护者不知道它到底允许改哪些字段。
- 参数校验更复杂。

所以当前项目使用 `TicketStatusUpdateDTO`，只接收 `status`，让接口边界更清晰。

## 6. DTO 和 Entity 有什么区别？

DTO 是 Data Transfer Object，意思是“数据传输对象”。

Entity 是实体类，通常对应数据库表。

在当前项目中：

| 类型 | 示例 | 作用 |
| --- | --- | --- |
| DTO | `TicketStatusUpdateDTO` | 接收前端请求参数 |
| Entity | `Ticket` | 对应数据库 `ticket` 表 |

`TicketStatusUpdateDTO` 只有一个字段：

```java
private String status;
```

它表示：这个接口只允许前端传状态。

`Ticket` 则包含完整数据库字段：

```java
id
title
content
status
priority
category
userId
createdAt
updatedAt
```

如果用 Entity 直接接收所有请求，接口很容易暴露过多字段。DTO 可以让每个接口只暴露自己需要的字段。

简单理解：

- DTO 面向接口请求或响应。
- Entity 面向数据库表。
- DTO 控制“前端能传什么”。
- Entity 控制“数据库有什么”。

## 7. BusinessException 在状态流转中负责什么？

`BusinessException` 用来表示业务上可预期的错误。

状态流转中可能出现很多不是系统崩溃、但业务不允许的情况：

- 工单不存在；
- 前端传了非法状态；
- 工单已经关闭，不能再修改；
- 状态流转不符合规则；
- 数据库更新失败。

这些都适合抛出 `BusinessException`。

例如：

```java
throw new BusinessException(400, "工单状态不合法");
```

然后 `GlobalExceptionHandler` 会统一捕获它，并返回统一 `Result`：

```json
{
  "code": 400,
  "message": "工单状态不合法",
  "data": null
}
```

这样 Controller 不需要到处写 `try-catch`，错误返回格式也保持一致。

## 8. Controller、Service、Mapper 在这个接口中分别做什么？

以 `PUT /tickets/{id}/status` 为例：

### Controller

Controller 负责 HTTP 层工作：

- 接收路径参数 `id`；
- 接收请求体 `TicketStatusUpdateDTO`；
- 使用 `@Valid` 触发参数校验；
- 调用 `ticketService.updateTicketStatus(id, dto.getStatus())`；
- 返回 `Result<Ticket>`。

当前项目中的 Controller 方法：

```java
@PutMapping("/{id}/status")
public Result<Ticket> updateTicketStatus(
        @PathVariable Long id,
        @Valid @RequestBody TicketStatusUpdateDTO dto) {

    Ticket updatedTicket = ticketService.updateTicketStatus(id, dto.getStatus());
    return Result.success("状态修改成功", updatedTicket);
}
```

### Service

Service 负责业务逻辑：

- 根据 id 查询工单；
- 判断工单是否存在；
- 校验新状态是否合法；
- 判断当前状态能否流转到新状态；
- 更新 `status` 和 `updatedAt`；
- 调用 Mapper 保存；
- 返回更新后的工单。

### Mapper

Mapper 负责数据库访问：

- `selectById(id)` 查询工单；
- `updateById(ticket)` 更新工单；
- `deleteById(id)` 删除工单；
- `selectPage(...)` 分页查询工单。

Mapper 不应该写业务判断，它只负责把数据从数据库读出来或写进去。

## 9. 为什么不应该在 Controller 中写大量 if-else 判断状态？

Controller 如果写大量状态判断，会出现几个问题：

- Controller 会变得很长，接口代码难读。
- 业务规则分散在 HTTP 层，后续复用困难。
- 如果另一个接口也需要状态流转，很可能复制一遍 if-else，导致重复代码。
- 修改规则时容易漏改某个 Controller。
- Controller 的职责会混乱，既处理 HTTP，又处理业务。

更合理的做法是：

```text
Controller 接收请求
        ↓
Service 判断状态流转规则
        ↓
Mapper 更新数据库
```

这样 Controller 保持轻量，Service 才是业务规则的中心。

## 10. 如果前端传了 ABC 这种非法状态，后端应该怎么处理？

后端不能相信前端传来的值。

如果前端传：

```json
{
  "status": "ABC"
}
```

后端应该：

1. 接收请求；
2. 在 Service 中把状态规范化，例如去空格、转大写；
3. 使用 `TicketStatus.isValid(status)` 判断是否合法；
4. 如果不合法，抛出 `BusinessException`；
5. 由 `GlobalExceptionHandler` 返回统一错误响应。

当前项目返回：

```json
{
  "code": 400,
  "message": "工单状态不合法",
  "data": null
}
```

这样做的意义是：

- 不让非法状态写入数据库；
- 给前端明确错误原因；
- 保持所有接口的错误格式一致；
- 避免后续查询、统计、流转时遇到未知状态。

## 小结

`PUT /tickets/{id}/status` 的调用链是：

```text
前端传入 id 和 status
        ↓
TicketController 接收请求
        ↓
TicketStatusUpdateDTO 校验 status 不能为空
        ↓
TicketService 查询工单并判断状态流转规则
        ↓
TicketMapper 更新数据库
        ↓
GlobalExceptionHandler 统一处理异常
        ↓
Controller 返回 Result<Ticket>
```

这个设计的重点是：

- DTO 控制接口入参；
- enum 控制合法状态范围；
- Service 控制业务规则；
- Mapper 控制数据库访问；
- BusinessException 和 GlobalExceptionHandler 控制统一错误返回。

# update_ticket_status Tool 说明

本文说明当前项目中的第四个 Agent Tool：`update_ticket_status`。它用于学习 Agent 如何执行受控业务动作，也就是从自然语言中提取业务参数，校验规则，然后修改 mock 工单数据。

## 1. update_ticket_status 是什么类型的 Tool

`update_ticket_status` 是一个写操作 Tool。

它会修改已有 mock 工单的 `status` 字段。当前项目没有连接数据库，也没有连接 Java 后端，所有工单都保存在进程内的 `MOCK_TICKETS` 列表中，所以这个修改只在当前服务进程内生效。

它的输入参数是：

```json
{
  "ticket_id": 3,
  "status": "PROCESSING"
}
```

它的成功输出结构是：

```json
{
  "success": true,
  "id": 3,
  "old_status": "OPEN",
  "new_status": "PROCESSING",
  "message": "工单状态修改成功"
}
```

## 2. 它和 search_tickets、create_ticket 有什么区别

`search_tickets` 是只读查询 Tool。它只读取 mock 工单数据，不会修改任何内容。

`create_ticket` 是创建类写操作 Tool。它会新增一条 mock 工单，默认状态是 `OPEN`。

`update_ticket_status` 是更新类写操作 Tool。它不会创建新工单，只会修改已存在工单的状态，并且必须通过状态流转规则校验。

简单区分：

```text
search_tickets        读取工单
create_ticket         新增工单
update_ticket_status  修改已有工单状态
```

## 3. ticket_id 如何从自然语言中提取

当前项目在 `AgentToolService` 中用简单正则规则提取工单 ID，不依赖大模型自动 Tool Calling。

支持的常见表达包括：

```text
把 3 号工单改成处理中
将 3 号工单状态改为已完成
把 id 为 3 的工单改成处理中
修改 3 号工单状态为处理中
```

这些表达会被提取为：

```json
{
  "ticket_id": 3
}
```

如果用户没有提供工单 ID，例如：

```text
把工单改成处理中
```

系统不会猜测要修改哪张工单，而是返回追问：

```text
修改工单状态还缺少必要信息：工单 ID。请说明要修改几号工单。
```

## 4. 中文状态如何映射成英文枚举

项目内部统一使用英文状态枚举：

```text
OPEN
PROCESSING
DONE
CLOSED
```

自然语言中的中文状态会先映射成英文枚举：

```text
未处理 / 待处理 / 打开 / open -> OPEN
处理中 / 处理 / processing -> PROCESSING
已完成 / 完成 / done -> DONE
已关闭 / 关闭 / closed -> CLOSED
```

例如：

```text
把 1 号工单改成处理中
```

会抽取为：

```json
{
  "ticket_id": 1,
  "status": "PROCESSING"
}
```

## 5. 什么是状态流转规则

状态流转规则是限制工单状态如何变化的业务规则。

当前项目使用严格单向流转：

```text
OPEN -> PROCESSING -> DONE -> CLOSED
```

允许的变化只有：

```text
OPEN -> PROCESSING
PROCESSING -> DONE
DONE -> CLOSED
```

不允许跳级，也不允许回退。例如：

```text
OPEN -> DONE       不允许
OPEN -> CLOSED     不允许
DONE -> OPEN       不允许
CLOSED -> OPEN     不允许
```

这样做是为了模拟真实业务中的受控动作：Agent 不能随意把工单改成任何状态，必须遵守业务流程。

## 6. 什么是非法状态

非法状态是指用户输入的目标状态无法映射到项目支持的状态枚举。

例如：

```text
把 3 号工单改成异常状态
```

`异常状态` 不是当前支持的状态，因此不会调用 `update_ticket_status`，也不会修改数据。

系统会返回：

```text
目标状态不合法。当前支持的状态包括：OPEN、PROCESSING、DONE、CLOSED；常用中文包括：未处理、处理中、已完成、已关闭。
```

## 7. 什么是非法流转

非法流转是指目标状态本身合法，但从当前状态改过去不符合业务流转规则。

例如 1 号工单当前是 `OPEN`，用户要求：

```text
把 1 号工单改成已完成
```

`DONE` 是合法状态，但 `OPEN -> DONE` 跳过了 `PROCESSING`，所以是非法流转。

系统会返回：

```text
状态流转不合法：当前状态 OPEN 不能修改为 DONE。允许的流转为 OPEN → PROCESSING → DONE → CLOSED。
```

如果工单已经是 `CLOSED`，也不能继续修改：

```text
状态流转不合法：工单已处于 CLOSED 状态，不能继续修改。
```

## 8. 工单不存在时如何处理

Tool 会根据 `ticket_id` 在 `MOCK_TICKETS` 中查找工单。

如果找不到，例如：

```text
把 999 号工单改成处理中
```

不会创建新工单，也不会修改任何数据，而是返回：

```text
没有找到 ID 为 999 的工单，无法修改状态。
```

这是业务错误，不是系统崩溃。系统不会把 Python 堆栈返回给前端。

## 9. 修改后为什么 search_tickets 能查到新状态

`search_tickets` 和 `update_ticket_status` 操作的是同一份进程内 mock 数据：

```text
app/tools/mock_ticket_data.py 中的 MOCK_TICKETS
```

`update_ticket_status` 修改的是 `MOCK_TICKETS` 中某个工单字典的 `status` 字段。

`search_tickets` 查询时也遍历同一个 `MOCK_TICKETS` 列表。

所以只要服务进程不重启，修改后的状态会立刻被 `search_tickets` 查到。例如：

```text
把 1 号工单改成处理中
查一下处理中工单
```

第二个查询会看到 1 号工单已经变成 `PROCESSING`。

## 10. 为什么 update_ticket_status 后续需要人工确认

`update_ticket_status` 是写操作，会改变业务数据。

当前阶段为了学习 Tool 调用链路，系统会直接执行修改，但真实业务中不应该让 Agent 在没有确认的情况下直接修改关键状态。

原因包括：

- 用户可能表达不清，Agent 可能提取错工单 ID。
- 用户可能说错目标状态。
- 状态修改可能影响后续处理流程、统计报表或客户通知。
- 已关闭、已完成等状态通常具有业务含义，不能随意更改。

后续可以引入 Human-in-the-loop：

```text
用户提出修改请求
-> Agent 抽取 ticket_id 和 status
-> 生成 pending action
-> 向用户展示将要执行的修改
-> 用户确认
-> 再调用 update_ticket_status
```

这样既能利用 Agent 自动抽取参数，又能避免未经确认的写操作直接影响业务数据。

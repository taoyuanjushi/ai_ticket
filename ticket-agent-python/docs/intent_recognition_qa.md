# 结构化意图识别学习复盘问答

本文用初学者能理解的方式，解释为什么本阶段要把 `/agent/chat` 里的正则路由升级为结构化意图识别，以及 `IntentResult`、structured output、缺字段追问和状态枚举一致性分别解决什么问题。

当前阶段的核心变化是：

```text
以前：
用户输入
↓
/agent/chat 里写很多 if / regex
↓
边判断意图，边抽参数，边调用工具

现在：
用户输入
↓
IntentRecognizer 统一识别
↓
得到 IntentResult
↓
AgentToolService 根据结构化结果分发工具
```

当前代码里的关键对象是：

```python
class IntentResult(BaseModel):
    intent: IntentType
    ticket_id: Optional[int] = None
    title: Optional[str] = None
    description: Optional[str] = None
    priority: Optional[str] = None
    target_status: Optional[str] = None
    keyword: Optional[str] = None
    confidence: float = 0.0
    missing_fields: list[str] = []
    raw_message: str
```

它的作用是：

```text
把一句自然语言
变成 Agent 能稳定理解和分发的结构化结果
```

## 1. 什么是意图识别？

意图识别就是判断用户这句话“想做什么”。

比如用户说：

```text
查一下我的工单
```

Agent 应该理解为：

```text
用户想查询工单
```

用户说：

```text
把 1 号工单改成处理中
```

Agent 应该理解为：

```text
用户想修改工单状态
工单 ID 是 1
目标状态是 PROCESSING
```

用户说：

```text
帮我给 3 号工单生成回复建议
```

Agent 应该理解为：

```text
用户想生成 AI 回复建议
工单 ID 是 3
```

所以意图识别不只是“看关键词”，它至少要回答两个问题：

```text
第一，用户想做哪类操作？
第二，这个操作需要的参数有没有说完整？
```

在当前项目里，意图被整理成 `IntentType`：

```text
QUERY_TICKET：查询工单
CREATE_TICKET：创建工单
UPDATE_TICKET_STATUS：修改工单状态
REPLY_SUGGESTION：生成回复建议
CONFIRM：确认执行
CANCEL：取消执行
UNKNOWN：无法判断
```

后续还预留了：

```text
TICKET_SUMMARY：工单摘要
PRIORITY_SUGGESTION：优先级建议
CATEGORY_SUGGESTION：分类建议
SIMILAR_TICKET_SEARCH：相似工单搜索
SLA_RISK_CHECK：SLA 风险提醒
```

这样后面扩展新能力时，不需要继续把逻辑塞进 `/agent/chat`。

## 2. 正则路由为什么适合 MVP，但不适合复杂 Agent？

MVP 是最小可用版本，目标是先把流程跑通。

在 MVP 阶段，用正则或关键词判断是合理的：

```python
if "查询" in message:
    search_tickets()

if "创建工单" in message:
    create_ticket()
```

它的优点是：

- 写起来快。
- 逻辑直观。
- 不依赖 LLM。
- 适合少量固定表达。

但系统变复杂后，正则路由会开始吃力。

比如用户可能这样说：

```text
帮我看一下高优先级未处理工单
把 2 号单子处理完
这个工单怎么回用户比较合适
先不改了
```

这些话里有些是查询，有些是修改，有些是回复建议，有些是取消。

如果所有判断都写在 `/agent/chat` 里，代码会慢慢变成：

```text
一堆 if
一堆 regex
一堆字段提取
一堆特殊情况
一堆工具调用
```

问题会越来越明显：

- 新增一个意图，要改很多地方。
- 规则顺序容易互相影响。
- “确认”“取消”这种短句可能被误判。
- 创建、修改、查询的字段提取逻辑混在一起。
- 测试很难覆盖所有组合。
- 后续接入 LLM 结构化识别时，没有清晰替换点。

所以正则适合 MVP，因为它快。

但复杂 Agent 更需要把意图识别单独抽成一层：

```text
/agent/chat 负责接收请求
IntentRecognizer 负责识别意图和参数
AgentToolService 负责根据意图调用工具
```

这样每一层只做自己的事，系统才好扩展。

## 3. 为什么要把用户输入转成 IntentResult？

因为自然语言太自由，程序不适合直接拿它做业务判断。

用户可能说：

```text
把 1 号工单改成处理中
```

程序真正需要的是：

```json
{
  "intent": "UPDATE_TICKET_STATUS",
  "ticket_id": 1,
  "target_status": "PROCESSING",
  "missing_fields": []
}
```

这就是 `IntentResult` 的意义。

它把一句话拆成稳定字段：

| 字段 | 含义 |
|---|---|
| `intent` | 用户想做什么 |
| `ticket_id` | 涉及几号工单 |
| `title` | 创建工单的标题 |
| `description` | 创建工单的描述 |
| `priority` | 创建工单的优先级 |
| `target_status` | 要修改成什么状态 |
| `keyword` | 查询关键词 |
| `confidence` | 识别信心 |
| `missing_fields` | 缺少哪些必要字段 |
| `raw_message` | 用户原始输入 |

这样后面的代码就不用再猜用户说了什么。

比如 `AgentToolService` 可以直接判断：

```text
intent 是 QUERY_TICKET
↓
调用查询工具

intent 是 CREATE_TICKET 且 missing_fields 为空
↓
创建 pending_action，等待用户确认

intent 是 CREATE_TICKET 但 missing_fields 不为空
↓
追问用户补字段，不创建 pending_action
```

简单说：

```text
IntentResult 是自然语言和业务工具之间的中间格式。
```

它让 Agent 从“靠猜一句话”变成“处理一个结构清楚的对象”。

## 4. 什么是 structured output？

`structured output` 可以翻译成“结构化输出”。

意思是：模型或识别器不要只返回一段自由文本，而是返回一个固定格式的数据对象。

普通文本输出可能是：

```text
用户好像想修改 1 号工单，把它改成处理中。
```

这句话人能看懂，但程序不好稳定处理。

结构化输出是：

```json
{
  "intent": "UPDATE_TICKET_STATUS",
  "ticket_id": 1,
  "target_status": "PROCESSING",
  "confidence": 0.85,
  "missing_fields": []
}
```

这种结果程序更容易使用，因为字段是固定的。

当前阶段的 `IntentResult` 就是一种 structured output。

虽然现在第一版仍然用规则识别，但目标已经变了：

```text
不再让规则直接决定调用哪个工具
而是先输出一个结构化的 IntentResult
再由 AgentToolService 分发
```

这样后续如果接入 LLM，也可以让 LLM 输出同样的结构：

```text
LLM 识别用户意图
↓
输出 IntentResult
↓
后面的工具调用逻辑不用大改
```

也就是说，structured output 是为了让 AI 的结果更像“程序可消费的数据”，而不是只像“人能读懂的一段话”。

## 5. 缺字段追问为什么比直接执行更安全？

因为有些操作必须参数完整才能执行。

比如创建工单至少需要：

```text
title：标题
description：描述
priority：优先级
```

如果用户只说：

```text
创建工单
```

Agent 不应该自己编一个标题、编一段描述、猜一个优先级。

如果它直接执行，就可能创建出错误工单：

```text
标题：未知问题
描述：用户未提供
优先级：MEDIUM
```

这类数据看起来能跑通，但业务上是脏数据。

修改状态也是一样。

用户说：

```text
把工单改成处理中
```

这里缺少工单 ID。

如果系统直接猜一个工单 ID，就可能改错别人的工单。

所以更安全的做法是：

```text
识别到用户想创建工单
↓
发现缺 title / description / priority
↓
不创建 pending_action
↓
追问用户补充信息
```

当前项目里就是通过 `missing_fields` 做这件事：

```json
{
  "intent": "CREATE_TICKET",
  "missing_fields": ["title", "description", "priority"]
}
```

这样 Agent 会回复：

```text
创建工单还缺少必要信息：标题、描述、优先级。请补充后我再创建。
```

缺字段追问的价值是：

- 不让 AI 编造关键业务字段。
- 不让写操作在信息不完整时进入确认态。
- 不误改工单。
- 不产生低质量数据。
- 让用户明确补充缺失信息。

一句话总结：

```text
缺字段时追问，是为了让 Agent 少猜、多确认。
```

## 6. 为什么状态映射必须和 Java 枚举一致？

因为 Java 后端才是真正执行工单业务规则的地方。

当前系统里，工单状态应该统一使用 Java 支持的枚举：

```text
OPEN
PROCESSING
CLOSED
```

Python Agent 可以理解中文：

```text
处理中
已完成
关闭
处理完
```

但它最终传给 Java 时，必须变成 Java 认识的值：

```text
处理中 -> PROCESSING
已完成 -> CLOSED
关闭 -> CLOSED
处理完 -> CLOSED
```

不能传一个 Java 不认识的值，比如：

```text
DONE
FINISHED
COMPLETE
已完成
```

如果 Python 输出了 `DONE`，但 Java 枚举里没有 `DONE`，就会出现问题：

```text
Python 认为识别成功
↓
调用 Java 更新状态
↓
Java 不认识 DONE
↓
接口报错或状态流转失败
```

更严重的是，如果 Python 和 Java 对状态含义不一致，用户看到的确认信息也会不可靠。

比如用户说：

```text
把 2 号工单改成已完成
```

Python 必须明确映射成：

```text
CLOSED
```

然后确认提示应该是：

```text
请确认：是否将 2 号工单状态修改为 CLOSED？
```

这样用户确认的内容和 Java 最终执行的内容是一致的。

状态映射保持一致的好处是：

- Java 能正常接收请求。
- 状态流转规则集中在 Java 后端。
- Python 不会创造出 Java 不支持的状态。
- 用户确认内容和实际执行内容一致。
- 前端、Java、Python 三端看到的状态不会打架。

所以本阶段特别要求：

```text
“完成 / 已完成 / 处理完” 必须映射为 CLOSED
不允许输出 DONE
```

这不是文字偏好，而是系统边界问题：

```text
Python 可以理解用户语言
Java 决定业务状态枚举
两边必须用同一套状态值对话
```

## 总结

本阶段结构化意图识别的核心价值是：

```text
把用户自由输入
先变成稳定的 IntentResult
再由工具层按结构化结果执行或追问
```

它解决的是 Agent 复杂起来后的几个关键问题：

- `/agent/chat` 不再堆大量正则和 if。
- 意图、参数、缺字段信息有统一格式。
- 查询可以直接执行。
- 创建、修改状态等写操作仍然进入确认态。
- 缺字段时先追问，不乱猜。
- 状态值和 Java 枚举保持一致。
- 后续可以平滑替换成 LLM 结构化识别。

可以记住一句话：

```text
意图识别不是为了让 Agent 更会聊天，而是为了让 Agent 更安全、更稳定地调用工具。
```

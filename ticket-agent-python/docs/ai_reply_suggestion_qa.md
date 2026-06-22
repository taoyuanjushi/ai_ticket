# AI 回复建议能力学习复盘问答

本文用初学者能理解的方式，解释第四阶段“真正 AI 回复建议”为什么这样设计。

当前阶段的核心链路是：

```text
客服或 Postman 请求 Python AI 服务
↓
POST /ai/tickets/{ticket_id}/reply-suggestion
↓
Python 通过 JavaTicketClient 调用 Java GET /tickets/{id}/detail
↓
Java 返回真实工单、提交人、历史回复
↓
PromptBuilder 把工单详情整理成 prompt
↓
LLMClient 调用 mock LLM 或真实 LLM
↓
Python 返回 AI 回复建议
```

本阶段只生成建议，不自动保存到数据库。

## 1. mock 回复建议和 LLM 真实生成有什么区别？

mock 回复建议是本地写死的一段固定文本。

比如当前 mock 模式可能一直返回：

```text
建议先向用户确认问题是否仍然存在，并请用户提供相关错误截图或具体操作步骤，以便进一步定位原因。
```

它的优点是稳定、简单、不需要 API Key，也不需要真实大模型服务。适合本地测试接口是否能跑通。

但 mock 的缺点也很明显：

```text
不理解当前工单内容
不理解历史回复
不会根据不同问题生成不同建议
```

LLM 真实生成是指 Python 把真实工单详情整理成 prompt，再调用大模型生成回复建议。它会根据不同工单内容输出不同建议。

简单理解：

```text
mock：固定演示答案
LLM：基于上下文动态生成答案
```

## 2. 为什么回复建议必须基于 GET /tickets/{id}/detail 的真实上下文？

因为回复建议不是闲聊，而是要帮助客服处理某一张真实工单。

`GET /tickets/{id}/detail` 返回的不只是工单标题，还包括：

```text
ticket：工单本身
user：提交人信息
replies：历史回复列表
```

AI 只有看到这些真实上下文，才能生成更贴近当前问题的建议。

例如：

```text
工单标题：登录失败
历史回复：已让用户提供错误截图
```

AI 的建议就应该继续围绕登录失败、错误截图、复现步骤展开，而不是给出泛泛的客服话术。

如果不使用真实详情，AI 很容易生成和当前工单无关的内容。

## 3. 为什么 Python AI 服务不应该直接查 MySQL？

因为当前系统里，Java 后端才是工单业务和数据库的正式入口。

Java 后端负责：

- 登录认证。
- JWT 校验。
- 用户权限判断。
- 工单详情查询。
- 回复列表查询。
- 数据库表结构管理。
- Redis 缓存。
- 统一异常和统一返回格式。

如果 Python 直接查 MySQL，就会绕过这些规则。

比如某个用户本来没有权限看某张工单，但 Python 如果直接查数据库，就可能把这张工单详情拿出来给 AI。这样会破坏权限边界。

所以 Python 不应该碰数据库，应该通过 Java API 获取数据。

## 4. 为什么 Python 通过 Java API 获取工单详情更合理？

因为 Java API 是工单系统已经定义好的正式服务接口。

Python 调 Java API 有几个好处：

- 不需要知道 MySQL 表结构。
- 不重复实现 Java 的权限逻辑。
- 不绕过 Java 的业务规则。
- Java 修改数据库结构时，Python 影响更小。
- Java 可以继续统一处理缓存、权限、异常。

当前正确链路是：

```text
Python AI 服务
↓
JavaTicketClient
↓
Java GET /tickets/{id}/detail
↓
Java Service 查询和组装详情
↓
Python 拿到结果后生成 AI 建议
```

这样职责边界清晰：

```text
Java 管真实业务数据
Python 管 AI 生成能力
```

## 5. JavaTicketClient.get_ticket_detail 应该负责什么？

`JavaTicketClient.get_ticket_detail` 是 Python 调 Java 工单详情接口的客户端方法。

它应该负责：

- 拼接 Java 请求路径：`/tickets/{id}/detail`。
- 带上 JWT Token。
- 发起 HTTP GET 请求。
- 解析 Java 统一 `Result` 返回。
- 成功时取出 `data`。
- Java 返回 401 / 403 / 404 时，转换成清晰错误。

它不应该负责：

- 构造 prompt。
- 调用 LLM。
- 生成回复建议。
- 保存 AI 回复。
- 直接查询 MySQL。

一句话：

```text
JavaTicketClient 只负责“怎么请求 Java”。
```

## 6. LLMClient 应该负责什么？

`LLMClient` 是专门负责调用大模型的客户端。

它应该负责：

- 判断是否启用 mock 模式。
- 在 mock 模式下返回本地固定建议。
- 在真实模式下调用 OpenAI-compatible LLM API。
- 读取 `LLM_API_KEY`、`LLM_API_BASE_URL`、`LLM_MODEL`。
- 处理 LLM 连接失败、HTTP 错误、返回格式错误。
- 从 LLM 响应中取出文本内容。

它不应该负责：

- 调 Java API。
- 理解工单业务规则。
- 查询数据库。
- 决定接口路径。
- 保存 TicketReply。

一句话：

```text
LLMClient 只负责“怎么调用模型并拿到文本”。
```

## 7. PromptBuilder 应该负责什么？

`PromptBuilder` 负责把结构化的工单详情转换成 LLM 能理解的提示词。

Java 返回的是结构化数据：

```json
{
  "ticket": {},
  "user": {},
  "replies": []
}
```

LLM 更适合阅读自然语言上下文，所以 PromptBuilder 要把这些数据整理成：

```text
你是企业工单系统中的客服助手。

工单信息：
- 标题：登录失败
- 内容：用户输入正确密码后仍提示错误
- 状态：OPEN
- 优先级：HIGH

历史回复：
1. [STAFF] 请用户提供错误截图

请生成客服回复建议。
```

PromptBuilder 还应该限制 AI：

- 只能生成建议。
- 不要编造不存在的信息。
- 不要保存数据库。
- 不要输出敏感信息。
- 不要把 `password` 放进 prompt。

一句话：

```text
PromptBuilder 负责“把真实工单详情整理成安全、清晰的 prompt”。
```

## 8. 为什么要把 prompt 构造、LLM 调用、接口路由分层？

分层是为了让代码更清楚，也更容易维护。

当前可以这样理解：

```text
Router：接收 HTTP 请求
TicketAiService：组织业务流程
JavaTicketClient：调用 Java API
PromptBuilder：构造 prompt
LLMClient：调用 LLM
Schema：定义请求和响应格式
```

如果把所有逻辑都写在 Router 里，会出现问题：

- 路由文件变得很长。
- 测试困难。
- Prompt 修改会影响接口代码。
- LLM 调用失败不好单独测试。
- Java API 调用逻辑会到处重复。

分层后，每个模块只做一件事。

例如以后 Java 接口路径变化，只改 `JavaTicketClient`。以后 prompt 要优化，只改 `PromptBuilder`。以后换模型服务，只改 `LLMClient`。

## 9. 为什么 AI 回复建议不能默认自动保存到数据库？

因为 AI 生成的内容不一定完全正确。

AI 可能出现这些问题：

- 理解错工单内容。
- 忽略某条历史回复。
- 语气不符合客服规范。
- 给出无法保证的承诺。
- 编造不存在的处理结果。

如果自动保存到数据库，用户或客服可能误以为这就是正式回复。

所以本阶段只做：

```text
生成建议
↓
返回给客服
↓
客服人工检查
```

不做：

```text
自动写入 ticket_reply
自动发送给用户
自动改变工单状态
```

这是一条安全边界。

## 10. TicketReplyType.AI 的作用是什么？

`TicketReplyType.AI` 用来标记“这条回复来自 AI 建议”。

在 Java 项目里，回复可能有不同来源：

```text
USER：用户回复
STAFF：客服回复
AI：AI 生成或辅助生成的回复
```

如果后续把 AI 建议保存到 `ticket_reply` 表，就可以把 `reply_type` 设置为：

```text
AI
```

这样系统就能区分：

```text
这是用户自己说的话
这是客服写的回复
这是 AI 生成的建议
```

这对审计、展示和后续优化都很重要。

## 11. 为什么 AI 生成内容需要人工确认？

因为 AI 是辅助工具，不是最终责任人。

客服人员需要确认：

- 建议是否符合事实。
- 是否适合当前用户。
- 是否遗漏了关键信息。
- 是否有不应该承诺的内容。
- 是否需要补充公司内部处理规范。

人工确认的意义是：

```text
AI 提供草稿
人负责判断和采纳
系统再保存或发送
```

这样既能利用 AI 提高效率，又不会让 AI 直接替人做高风险决定。

## 12. 如何避免 AI 编造不存在的工单信息？

不能完全保证 AI 永远不编造，但可以降低风险。

当前阶段可以从几方面控制：

第一，prompt 明确要求：

```text
必须基于工单内容和历史回复，不要编造不存在的信息。
```

第二，只把真实 Java 工单详情放进 prompt，不让模型凭空猜。

第三，信息不足时要求 AI 建议客服补充信息，而不是自己编结果。

第四，不让 AI 执行写操作，只返回建议文本。

第五，让客服人工确认后再采纳。

第六，测试 prompt，确认里面包含标题、内容、状态、优先级、分类、历史回复。

简单说：

```text
真实上下文 + 明确限制 + 人工确认
```

可以显著降低 AI 编造带来的风险。

## 13. LLM 调用失败时应该如何降级？

LLM 调用失败时，不应该让用户看到 Python 堆栈。

应该返回清晰的错误，例如：

```text
生成 AI 回复建议失败：LLM 服务调用失败
```

常见情况有：

- LLM 服务地址写错。
- API Key 错误。
- 模型名错误。
- 网络超时。
- LLM 返回格式不符合预期。

本地开发时可以使用 mock 模式降级：

```env
LLM_MOCK_MODE=true
```

这样即使没有真实 LLM，也能返回固定建议，方便测试接口链路。

但要注意：

```text
mock 只能证明接口流程可用
不能证明模型真的理解了工单
```

真实环境里，如果 `LLM_MOCK_MODE=false` 且配置不完整，就应该明确提示检查配置。

## 14. LLM_API_KEY 为什么必须放在环境变量里？

因为 `LLM_API_KEY` 是敏感凭证。

如果写死在代码或 README 里，会有风险：

- 代码提交后 key 被别人看到。
- key 可能被滥用，产生费用。
- 生产和测试环境无法灵活切换。
- 换 key 时必须改代码。

放在环境变量中更合理：

```env
LLM_API_KEY=你的本地测试 key
```

代码只读取配置：

```text
settings.llm_api_key
```

这样可以做到：

```text
代码不含密钥
不同环境使用不同 key
泄露风险更低
```

`.env` 不能提交，`.env.example` 只能放空值示例。

## 15. 这一阶段如何证明 AI 服务已经从 mock 升级为真实 AI 能力？

可以从几个方面验证。

第一，Python 已经能调用 Java 工单详情接口：

```text
JavaTicketClient.get_ticket_detail
↓
GET /tickets/{id}/detail
```

第二，prompt 不是固定文本，而是由真实工单详情构造：

```text
工单标题
工单内容
状态
优先级
分类
历史回复
```

第三，`LLM_MOCK_MODE=false` 时，`LLMClient` 会调用真实 LLM API：

```text
{LLM_API_BASE_URL}/chat/completions
```

第四，换不同工单 ID，prompt 上下文会变化，真实 LLM 输出也应该跟着变化。

第五，Java 未启动或工单不存在时，接口会报获取详情失败，而不是继续返回本地假数据。

第六，测试链路可以这样做：

```text
1. 启动 Java 后端
2. 登录 Java 获取 JWT
3. 调 Java GET /tickets/1/detail 确认有真实详情
4. 配置 LLM_MOCK_MODE=false、LLM_API_KEY、LLM_API_BASE_URL、LLM_MODEL
5. 调 Python POST /ai/tickets/1/reply-suggestion
6. 查看返回建议是否围绕 1 号工单内容生成
```

如果这些都成立，就说明：

```text
AI 服务不再只是返回固定 mock 文本
而是已经基于真实 Java 工单详情调用 LLM 生成建议
```

## 总结

第四阶段的重点不是让 AI 自动处理工单，而是让 AI 成为客服的“建议助手”。

正确边界是：

```text
Java 负责真实工单数据、权限和数据库
Python 负责获取上下文、构造 prompt、调用 LLM
AI 只生成建议
客服负责人工确认
后续 Java 再保存被采纳的 AI 建议
```

这样既能引入真实 AI 能力，又不会破坏原有业务系统的安全边界。

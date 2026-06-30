一、权限边界相关
1. 为什么前端路由权限不能作为真正的安全边界？

参考答案：

因为前端代码运行在用户浏览器中，用户可以通过浏览器控制台、Postman、curl 等方式绕过前端页面，直接请求后端接口。前端权限只能用于体验控制，比如隐藏按钮和菜单。真正的安全必须在后端完成，尤其是在 Service 层校验用户身份、角色和资源归属。

2. JWT 在权限系统中的作用是什么？

参考答案：

JWT 用于在用户登录后携带身份信息。服务端签发 token，客户端后续请求时在请求头中携带 token。后端验证签名和过期时间后，可以解析出 userId、role 等信息，用于权限校验。JWT 的优点是无状态，缺点是 token 失效和主动注销需要额外机制处理。

3. 什么是资源归属校验？为什么只校验角色不够？

参考答案：

资源归属校验是判断当前用户是否有权访问某个具体资源。例如普通用户只能查看自己创建的工单。只校验角色不够，因为两个用户都是 USER，但用户 A 不能访问用户 B 的工单。因此权限判断不能只看“你是什么角色”，还要看“这个资源是不是你的”。

4. 为什么建议在 Service 层做权限校验？

参考答案：

Controller 层容易因为新增接口、复用方法或遗漏校验而产生绕过风险。Service 层是业务逻辑的集中位置，不管请求来自哪个 Controller，只要最终调用 Service，都能统一执行权限判断。因此 Service 层更适合作为核心权限边界。

二、Human-in-the-loop 相关
5. 为什么 AI 不能直接写数据库？

参考答案：

AI 可能误解用户意图、生成错误参数、受到提示词攻击，或者在上下文不完整时做出错误判断。涉及创建、修改、删除、关闭、分配等写操作时，必须让人类确认。AI 应该生成建议或待确认动作，由用户确认后，再由后端业务服务执行。

6. pending_action 表的作用是什么？

参考答案：

pending_action 表用于保存 AI 识别出的待确认操作，包括操作类型、目标资源、参数、发起用户、会话 ID、状态、过期时间、确认时间、执行时间等。它把“AI 生成意图”和“真正执行业务操作”解耦，使系统可以支持确认、取消、过期、审计和幂等。

7. 什么是幂等？在 AI 确认流程中为什么重要？

参考答案：

幂等是指同一个操作执行一次和执行多次的最终效果一致。在 AI 确认流程中，用户可能重复点击确认，网络也可能重试请求。如果没有幂等机制，可能重复创建工单或重复修改状态。可以通过 pending_action 状态、idempotency_key、数据库事务、唯一索引和乐观锁保证幂等。

8. pending_action 应该有哪些状态？

参考答案：

常见状态包括 PENDING、CONFIRMED、EXECUTED、CANCELLED、EXPIRED、FAILED。简单实现可以使用 PENDING、EXECUTED、CANCELLED、EXPIRED、FAILED。严格实现可以把 CONFIRMED 和 EXECUTED 分开，用于区分“用户已确认”和“业务执行完成”。

9. 为什么需要审计日志？

参考答案：

审计日志用于记录谁发起了 AI 操作、AI 识别了什么、用户确认了什么、最终执行了什么、执行是否成功。它可以用于问题排查、安全追责、合规审计，也可以帮助开发者分析 AI 误判和用户行为。

三、结构化 AI 输出相关
10. 为什么 AI 输出建议使用 JSON，而不是只返回自然语言？

参考答案：

自然语言适合人阅读，但不适合程序稳定解析。JSON 可以明确表达 intent、target、payload、confidence、risk_flags、fallback 等字段，方便后端校验、前端展示和业务逻辑处理。结构化输出还能降低 AI 幻觉带来的系统风险。

11. Pydantic 在 AI 服务中有什么作用？

参考答案：

Pydantic 可以定义数据结构和字段约束，对 AI 输出的 JSON 进行校验。例如 confidence 必须在 0 到 1 之间，intent 必须属于指定枚举，ticket_id 可以为空但某些 action 必须存在。校验失败时，系统可以进入 fallback，而不是继续执行不可靠结果。

12. confidence 和 risk_flags 分别解决什么问题？

参考答案：

confidence 表示 AI 对自己判断的置信度，方便系统决定是否直接展示、是否要求用户确认、是否进入人工审核。risk_flags 是风险标签，用来表达具体风险类型，例如 low_confidence、missing_required_fields、permission_sensitive、destructive_action、sla_risk 等。二者结合可以让系统更可控。

13. fallback 的作用是什么？

参考答案：

fallback 是 AI 在无法确定用户意图或缺少必要信息时返回的降级处理。比如用户说“把那个问题处理了”，但没有指定工单 ID，AI 应该返回 fallback：“请提供需要处理的工单编号”，而不是猜测并生成危险操作。

四、前后端状态同步相关
14. React Query 的 invalidateQueries 是什么作用？

参考答案：

invalidateQueries 用于让指定 queryKey 对应的缓存失效，并触发重新请求。AI 确认操作成功后，后端数据已经变化，前端需要刷新工单详情、回复列表、日志、工单列表和 Dashboard，否则页面会显示旧数据。

15. AI 确认保存回复后，前端应该刷新哪些数据？

参考答案：

至少应该刷新当前工单详情、回复列表、操作日志。如果这个操作会影响列表状态或统计数据，还应该刷新工单列表和 Dashboard。如果 AI 建议本身有状态，比如“待确认、已采纳、已拒绝”，也要刷新 AI 建议列表或详情。

16. 什么是乐观更新？什么时候不适合使用？

参考答案：

乐观更新是指前端先假设请求成功，立即更新 UI，后端失败时再回滚。它适合点赞、收藏等低风险操作。不适合高风险写操作，比如关闭工单、修改 SLA、删除数据。AI 确认类操作更推荐先显示 loading，后端成功后统一 invalidateQueries 刷新。

五、SLA 建模相关
17. 为什么说 SLA 不能只靠 AI 提醒？

参考答案：

AI 提醒是非确定性的建议，不能作为业务规则。真正的 SLA 应该有明确字段、明确计算规则和明确状态，例如 responseDueAt、resolveDueAt、firstRespondedAt、resolvedAt、slaStatus。AI 可以基于这些真实数据做解释和建议，但不能替代业务规则本身。

18. 工单系统中常见的 SLA 字段有哪些？

参考答案：

常见字段包括 responseDueAt、resolveDueAt、firstRespondedAt、resolvedAt、closedAt、slaStatus、responseBreached、resolveBreached。简单项目可以先放在 ticket 表中，复杂项目可以拆成 ticket_sla 表。

19. 如何判断一个工单是否响应超时？

参考答案：

如果当前时间大于 responseDueAt，并且 firstRespondedAt 为空，就说明首次响应超时。如果 firstRespondedAt 不为空，还需要判断 firstRespondedAt 是否晚于 responseDueAt。

20. SLA 为什么需要索引？

参考答案：

系统通常需要频繁查询即将超时、已经超时、未关闭的工单。如果没有索引，随着工单数量增加，扫描成本会变高。可以给 responseDueAt、resolveDueAt、status、slaStatus 等字段建立组合索引，提高查询和定时任务扫描效率。

综合面试题
21. 请设计一个“AI 修改工单状态”的安全流程。

参考答案：

流程如下：

用户登录后携带 JWT 请求 AI 接口；
Java 后端解析 JWT，得到 userId 和 role；
AI 服务识别用户意图，输出结构化 JSON；
Java 校验 JSON，包括 intent、ticketId、目标状态、confidence、risk_flags；
Service 层校验当前用户是否有权操作该工单；
对写操作不直接执行，而是创建 pending_action；
前端展示确认信息；
用户点击确认；
后端检查 pending_action 是否存在、是否属于当前用户、是否过期、是否仍是 PENDING；
使用事务和幂等机制执行状态修改；
写入审计日志；
前端 invalidateQueries，刷新详情、列表、日志和 Dashboard。
22. 如果用户重复点击“确认 AI 操作”，你怎么防止重复执行？

参考答案：

可以从前端和后端同时处理。前端在点击确认后禁用按钮，避免重复提交。后端不能依赖前端，需要检查 pending_action 状态，只有 PENDING 可以执行；执行时在事务中把状态改为 EXECUTED；可以增加 version 乐观锁或唯一 idempotency_key，保证并发请求只有一个成功。

23. 如果 AI 输出了不合法 JSON，系统应该怎么处理？

参考答案：

系统不应该继续执行业务操作。Python 服务可以用 Pydantic 校验 AI 输出，如果校验失败，返回 fallback，例如“我没有理解你的请求，请补充工单编号或操作类型”。后端也应该再次校验 DTO，避免不合法数据进入业务层。

24. ADMIN 和 STAFF 在分配工单功能上的权限应该如何设计？

参考答案：

ADMIN 可以选择任意 STAFF 或 ADMIN 作为处理人，也可以取消分配。STAFF 通常只能领取未分配工单，或者处理分配给自己的工单。USER 不能分配工单。Service 层需要校验当前用户角色、目标处理人角色、工单当前状态以及资源操作是否合法。

25. AI SLA 风险建议和真实 SLA 计算有什么区别？

参考答案：

真实 SLA 计算基于数据库字段和确定性业务规则，例如 responseDueAt、resolveDueAt、firstRespondedAt、resolvedAt。AI SLA 风险建议是基于上下文生成的解释和建议，可能有不确定性。正确做法是先用业务规则算出真实 SLA 状态，再让 AI 解释为什么有风险，以及建议如何处理。
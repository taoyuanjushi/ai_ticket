# Learning Guide

| 问题 | 简短解释 | 当前项目例子 | 重点掌握 |
| --- | --- | --- | --- |
| 1. 为什么先修 bug 而不是加功能？ | 旧问题会污染新功能判断。 | 权限、AI 转发、pending_action 是核心链路。 | 先稳主链路。 |
| 2. 什么叫最小化修复？ | 只改导致问题的最小位置。 | 删除空 root lock，不动前端依赖。 | 少改、可验证。 |
| 3. 怎么判断功能可删除？ | 全局无引用、无运行入口、无文档依赖。 | `example.lnk` 无引用且不是源码。 | 删除前搜索。 |
| 4. 前端权限和后端权限区别？ | 前端控制显示，后端控制安全。 | 菜单隐藏 users，Java 仍用 `PermissionUtil` 校验。 | 后端必须兜底。 |
| 5. 为什么前端不能直调 Python？ | 会绕过 Java 权限和审计。 | 前端只调 `/ai/chat`，Java 再调 `/agent/chat`。 | 统一入口。 |
| 6. JWT 里保存什么？ | userId、username、role、过期时间。 | `JwtUtil` subject 是 userId，claim 有 role。 | 少放敏感数据。 |
| 7. 三类角色边界怎么设计？ | USER 看自己，STAFF 处理工单，ADMIN 管理全局。 | `TicketService.checkTicketReadable` 限制 USER。 | 权限矩阵。 |
| 8. 什么是工单详情闭环？ | 创建、查看、回复、状态更新形成完整流程。 | `TicketDetailPage` + replies + status。 | 围绕单个 ticket。 |
| 9. 普通回复和 AI 回复为何分开？ | 来源不同，可信度和责任不同。 | `TicketReplyType.USER/STAFF/AI`。 | 类型清晰。 |
| 10. AI 建议为何不能直接发给用户？ | AI 可能错，需人工确认。 | 保存 AI 回复走 pending_action。 | 人工把关。 |
| 11. 什么是 Human-in-the-loop？ | 人确认后系统才执行高风险动作。 | 创建/改状态需回复“确认”。 | 写操作加确认。 |
| 12. pending_action 为何按会话隔离？ | 防止 A 会话确认 B 会话操作。 | Java 用 `userId + conversationId` 查询。 | 隔离维度。 |
| 13. Redis TTL 有什么用？ | 自动过期临时状态。 | Python pending_intent 默认 10 分钟。 | 状态不能永久挂起。 |
| 14. 什么是幂等性？ | 重复请求不会造成重复副作用。 | Java confirm 用状态条件更新 pending。 | 防重复执行。 |
| 15. 为什么写操作需要 confirmation_token？ | 用来绑定“这次确认”和“这次待执行动作”。 | 当前用 conversationId 绑定，后续可升级 token。 | 确认凭证。 |
| 16. 什么是审计日志？ | 记录谁在何时做了什么结果如何。 | `operation_log` 记录工单和 AI 操作。 | 可追踪。 |
| 17. 审计日志和普通日志区别？ | 审计给业务追责，普通日志给排错。 | operation_log 入库，应用日志进控制台。 | 审计要持久。 |
| 18. Agent 为什么记录 tool call？ | 方便追踪 AI 实际执行了什么。 | Python 调 Java 查询/创建/pending。 | 输入、工具、结果。 |
| 19. 什么是结构化输出？ | 用固定字段返回结果。 | `type/message/data/risk_flags`。 | 前端可稳定渲染。 |
| 20. JSON schema 有什么帮助？ | 限制字段、类型和必填项。 | Pydantic schema 校验 Agent 请求响应。 | 减少解析歧义。 |
| 21. confidence 等于正确率吗？ | 不等于，只是模型自评或规则评分。 | 分类建议 confidence 不能当真理。 | 结合人工判断。 |
| 22. risk_flags 怎么设计？ | 用短标签说明风险。 | `risk_flags` 可标记权限、信息不足、LLM 失败。 | 少而明确。 |
| 23. Docker Compose 解决什么？ | 一条命令组合多服务。 | MySQL、Redis、Java、Python、前端一起跑。 | 服务编排。 |
| 24. Nginx 反代作用？ | 前端统一访问 `/api`，隐藏后端内部地址。 | `frontend/nginx.conf` 代理 Java。 | 生产路由。 |
| 25. 部署后为何不能硬编码 localhost？ | 容器内 localhost 指自己。 | compose 中 Java 用 `python-ai:8001`。 | 用服务名/env。 |
| 26. dev/prod 配置怎么分？ | dev 方便本地，prod 用环境变量和安全值。 | `application.properties` 支持 `${ENV:default}`。 | 生产覆盖默认。 |
| 27. Service 层为何不能写太多 Controller 逻辑？ | Controller 管 HTTP，Service 管业务。 | `TicketController` 调 `TicketService`。 | 分层职责。 |
| 28. DTO/Entity/VO 区别？ | DTO 入参，Entity 数据库，VO 出参。 | `TicketCreateDTO`、`Ticket`、`TicketDetailVO`。 | 不混用。 |
| 29. 状态流转为何集中管理？ | 避免各处规则不一致。 | `TicketStatusTransitionPolicy`。 | 单点规则。 |
| 30. 怎么写进简历？ | 写业务目标、技术栈、个人贡献、可量化结果。 | “实现 Java+Python AI 工单系统，AI 写操作支持人工确认和审计”。 | 讲清问题和结果。 |


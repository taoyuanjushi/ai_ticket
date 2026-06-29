# Next Stage Plan

## 1. 当前已经具备的能力

- USER / STAFF / ADMIN 三类角色登录、导航和权限展示。
- Java JWT 认证、当前用户上下文、工单 CRUD、回复、状态流转、操作日志。
- 前端通过 Java `/ai/chat` 使用 AI，不直接调用 Python。
- Python Agent 支持查询工单、创建工单、修改状态、回复建议、摘要、优先级/分类建议、SLA 风险、相似工单。
- AI 写操作通过 Java `pending_action` 做 Human-in-the-loop 确认。
- Docker Compose 已定义 MySQL、Redis、Java、Python、前端 Nginx。

## 2. 当前还缺少的关键能力

| 缺口 | 说明 |
| --- | --- |
| AI 操作审计还不够结构化 | 目前已有 `operation_log`，但不是专门的 AI 操作链路审计表。 |
| 工单回复闭环已部分实现 | 详情页、回复、AI 建议已有，但“采纳后编辑再发送”的体验还可以更完整。 |
| 管理统计较弱 | 目前有列表页局部统计，没有 ADMIN 全局 Dashboard。 |
| 完整 Docker 验收需要补跑 | 本次 Docker daemon 未运行。 |

## 3. 下一阶段最应该做的 3 个功能

### 任务 1：工单详情与回复闭环

目标：让用户、员工、管理员围绕一个工单完成完整沟通。

前端需要：
- 保留现有 `TicketDetailPage`。
- 增强回复列表展示发言人姓名和角色。
- STAFF 生成 AI 回复建议后，可以编辑后再发送。
- 保存 AI 回复仍必须经过 pending_action。

Java 需要：
- 复用现有 `/tickets/{id}/detail`、`/tickets/{id}/replies`、`/tickets/{id}/ai-replies/pending`。
- 确认 `TicketReplyType.USER / STAFF / AI` 在所有返回中稳定。
- 必要时给回复 VO 增加展示名，不改表优先。

Python 需要：
- 继续基于真实工单详情生成结构化建议。
- 输出 `suggestion / confidence / reason / risk_flags`。
- 不编造不存在的工单信息。

验收标准：
- USER 只能看自己的详情和回复。
- STAFF 可以看自己负责或待处理工单。
- ADMIN 可以看全部。
- AI 建议不直接发给用户。
- STAFF 采纳后才成为正式回复。

### 任务 2：AI 操作审计日志

目标：让 AI 查询、建议、pending、确认、取消、失败都可追踪。

前端需要：
- 在现有日志页上增加 AI 操作筛选优先，不新建复杂页面。

Java 需要：
- 优先复用 `operation_log`，先不要新表。
- 记录 `conversationId`、intent、actionType、结果摘要。
- 只有现有表无法承载时，再考虑 `ai_operation_log`。

Python 需要：
- 在 Agent 响应里稳定返回 intent/actionType/riskFlags。
- 对工具调用前后记录可审计字段，不记录 token。

验收标准：
- ADMIN 能筛选 AI 查询、创建、修改状态、保存回复、分类采纳。
- 失败、无权限、取消都有记录。
- 能按一次 conversationId 追踪完整链路。

### 任务 3：管理员 Dashboard

目标：提升项目展示效果，但保持简单。

前端需要：
- ADMIN 首页或独立页面展示统计卡片。
- 先做数字卡片，不急着引入图表库。

Java 需要：
- 提供只读统计接口：总数、待处理、处理中、已关闭、优先级分布。
- 使用真实数据库聚合。

Python 需要：
- 暂时不需要改。

验收标准：
- ADMIN 能访问统计。
- USER/STAFF 不能访问。
- 刷新后数据来自真实数据库。

## 4. 不建议现在做的功能

- 多租户。
- 复杂 RAG。
- 替换前端/后端技术栈。
- 大规模重构 Python Agent。
- 引入大型图表库做复杂 Dashboard。

## 5. 学习路线

1. 先掌握权限边界：前端展示控制和后端强制校验。
2. 再掌握 Human-in-the-loop：pending_action、确认、取消、幂等。
3. 然后掌握 AI 输出治理：结构化 JSON、risk_flags、审计日志。
4. 最后学习部署验收：Docker Compose、Nginx 反代、环境变量覆盖。


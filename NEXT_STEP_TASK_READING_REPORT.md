# NEXT_STEP_TASK_READING_REPORT

## 1. TicketServiceCacheSecurityTest 当前失败现象

已运行：

```bash
.\mvnw.cmd "-Dtest=TicketServiceCacheSecurityTest" test
```

结果：`TicketServiceCacheSecurityTest` 共 23 个测试，5 个 error。

## 2. 失败堆栈关键异常

关键异常是：

```text
com.baomidou.mybatisplus.core.exceptions.MybatisPlusException:
can not find lambda cache for this entity [com.example.hello_demo.entity.Ticket]
```

堆栈入口在 `LambdaUpdateWrapper.set(...)`，最终来自 `TicketService.updateTicketCategory(...)` 和 `TicketService.updateTicketAssignee(...)`。

## 3. 是否和 MyBatis-Plus lambda-cache / TableInfo / LambdaMeta 有关

是。当前测试是纯 Mockito 单元测试，没有启动 MyBatis/Spring 上下文，所以 MyBatis-Plus 没有为 `Ticket` 初始化表元数据。`LambdaUpdateWrapper<Ticket>` 解析 `Ticket::getCategory`、`Ticket::getAssignedTo` 等 lambda 字段时找不到缓存。

## 4. 失败测试方法

失败方法为：

- `staffCanUpdateCategoryAndEvictCacheAndWriteLog`
- `adminCanClearCategory`
- `adminCanAssignTicketToStaffAndWriteLog`
- `adminCanClearAssignee`
- `staffCanAssignTicketToSelf`

## 5. 使用 LambdaUpdateWrapper 的业务方法

本次失败直接相关的方法：

- `TicketService.updateTicketCategory`
- `TicketService.updateTicketAssignee`

项目中其他服务也有 `LambdaUpdateWrapper`，例如 `AiPendingActionService`，但本次失败来自 `TicketServiceCacheSecurityTest` 覆盖的两个方法。

## 6. 当前测试类型

`TicketServiceCacheSecurityTest` 是纯单元测试：手动 `mock(...)` mapper/service，没有 `@SpringBootTest`、`@MybatisTest` 或真实数据库。

`TicketPermissionIntegrationTest` 使用 `MockMvc` standalone 风格，属于窄范围 controller 集成检查，也没有完整 Spring/MyBatis 上下文。

## 7. 当前最小修复方案

优先在 `TicketServiceCacheSecurityTest` 中初始化 `Ticket` 的 MyBatis-Plus TableInfo：

- 使用 `MybatisConfiguration`
- 使用 `MapperBuilderAssistant`
- 调用 `TableInfoHelper.initTableInfo(..., Ticket.class)`

这样不改业务代码，不改数据库，不删断言，只补齐纯单元测试缺少的 MyBatis-Plus 元数据。

## 8. Python AI Agent 疑似兼容代码列表

已发现的兼容/遗留候选：

- `JavaTicketClient.save_ai_reply(...)`
- `AiReplySaveRequest`
- `tests/test_java_ticket_client.py` 中直接保存 AI reply 的旧测试
- `tests/conftest.py` 中 FakeJavaTicketClient 的 `save_ai_reply(...)`
- `AgentStateService` 与 `pending_action_store`
- LangChain `@tool` / `BaseTool` 外壳
- `AgentToolService` 中未读取的 `llm_service` 字段

## 9. 可安全删除的 Python 兼容代码

优先删除旧的“直接保存 AI 回复”客户端入口：

- `AiReplySaveRequest`
- `JavaTicketClient.save_ai_reply(...)`
- 对应单元测试和 fake 方法

原因：当前正式保存 AI 回复应进入 Java-owned `pending_action`，直接 POST `/tickets/{id}/ai-replies` 已不是 Python Agent 写入路径。

## 10. 只能标记为疑似废弃的 Python 兼容代码

暂不删除：

- `AgentStateService`
- `pending_action_store`
- LangChain tool wrappers
- `llm_service` 注入

原因：仍被 route、测试或工具注册路径引用；一次性删除会扩大改动面。

## 11. 前端 operation-type 常量现状

`frontend/src/types/domain.ts` 已有 `OperationType` union，但缺少 Java 新增的若干 AI 审计 action。

`LogsPage.tsx` 的 action 下拉列表是手写数组，只列出部分 AI action，容易漏掉新枚举。

## 12. Java actionType / enum / constants 现状

Java 来源是：

```text
java/hello-demo/src/main/java/com/example/hello_demo/enums/OperationType.java
```

当前 enum 包含普通工单、登录注册、AI 查询、AI pending、AI 确认、AI 取消、AI 回复保存、AI 分类应用等 action。

## 13. 前端与 Java 不一致的 operation-type 列表

前端 `OperationType` 缺少：

- `AI_REPLY_SAVE_PENDING_CREATED`
- `AI_REPLY_SAVED`
- `AI_CATEGORY_APPLY_PENDING_CREATED`
- `AI_CATEGORY_APPLIED`
- `AI_ACTION_CANCELLED`
- `AI_ACTION_CONFIRM_FAILED`

`LogsPage.tsx` 下拉列表还缺少更多 Java 已有 action，例如 `AI_ERROR`、`AI_FORBIDDEN`、`AI_WRITE_CONFIRMED`、`AI_WRITE_CANCELLED` 等。

## 14. /tickets/{id}/detail 当前是否已有集成测试

已有权限相关检查：

- 普通用户不能查看他人工单详情
- Redis 缓存命中不能绕过权限

还没有专门保护 replies 展示契约的测试。

## 15. 缺少的断言

缺少：

- `replies` 返回数组
- `authorName`
- `authorRole`
- `replyType`
- `createdAt`
- 多条回复按 `createdAt` 升序展示

## 16. docker-compose.prod.yml 是否存在

存在：`docker-compose.prod.yml`。

同时存在：

- `.env.example`
- `frontend/nginx.conf`
- `frontend/Dockerfile`

## 17. 生产 Compose 验收需要的环境变量

主要变量来自 `.env.example`：

- MySQL：`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`
- Redis：`REDIS_HOST`、`REDIS_PORT`
- JWT：`JWT_SECRET`、`JWT_EXPIRATION`
- Java 调 Python：`AI_SERVICE_BASE_URL`
- Python 调 Java：`JAVA_API_BASE_URL`、`JAVA_API_TIMEOUT`
- LLM：`LLM_MOCK_MODE`、`LLM_API_KEY`、`LLM_API_BASE`、`LLM_MODEL`
- 前端：`VITE_API_BASE_URL`、`FRONTEND_PORT`
- 日志：`MYBATIS_LOG_IMPL`

## 18. 本轮计划修改项

- 修复 `TicketServiceCacheSecurityTest` 的 MyBatis-Plus TableInfo 初始化问题
- 删除 Python 旧的直接保存 AI 回复客户端兼容入口
- 让前端 operation type 常量和 Java `OperationType` 对齐
- 给 `/tickets/{id}/detail` 增加 replies 契约检查
- 检查 Docker 是否可用，可用则执行生产 Compose 验收，不可用则记录原因

## 19. 暂不修改项和原因

- 不改 `TicketService` 业务逻辑：失败根因在测试环境缺少 MyBatis-Plus 元数据
- 不改数据库表结构：本轮不需要 schema 变化
- 不删除 Python 内存 pending-action 相关文件：仍有测试/兼容引用，风险高于收益
- 不替换 LangChain tool 外壳：仍用于 `.invoke`、`.name`、`.description`，属于后续可做的独立简化
- 不重写 Agent：本轮只做低风险兼容清理
- 不改变 `/tickets/{id}/detail` 响应结构：只加测试保护现有契约

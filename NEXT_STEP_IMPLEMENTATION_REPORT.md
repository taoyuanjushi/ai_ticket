# NEXT_STEP_IMPLEMENTATION_REPORT

## 1. 本次阅读范围

已阅读并核对：

- `TicketServiceCacheSecurityTest`
- `TicketService`
- `TicketController`
- `TicketReply` / `TicketReplyVO` / `TicketDetailVO`
- `OperationType`
- `LogsPage.tsx`
- `domain.ts`
- Python `JavaTicketClient`、ticket schema、相关 tests/fake client
- `docker-compose.prod.yml`、`.env.example`、`frontend/nginx.conf`、`frontend/Dockerfile`

## 2. TicketServiceCacheSecurityTest 失败原因

失败来自纯 Mockito 单元测试中执行 `LambdaUpdateWrapper<Ticket>`。测试没有启动 MyBatis/Spring 上下文，MyBatis-Plus 没有初始化 `Ticket` 的 TableInfo。

## 3. MyBatis lambda-cache 根因

异常：

```text
can not find lambda cache for this entity [com.example.hello_demo.entity.Ticket]
```

`LambdaUpdateWrapper` 解析 `Ticket::getCategory`、`Ticket::getAssignedTo` 等 lambda 字段时依赖 MyBatis-Plus TableInfo/lambda cache。纯单元测试缺少这一步初始化。

## 4. 采用的修复方案

在 `TicketServiceCacheSecurityTest` 中用 `TableInfoHelper.initTableInfo(...)` 初始化 `Ticket` 元数据。

同时在 `TicketPermissionIntegrationTest` 中初始化 `TicketReply` 元数据，因为新增 detail 测试会走 `LambdaQueryWrapper<TicketReply>`。

## 5. 未选择其他方案的原因

- 未改 `TicketService`：业务逻辑没有坏，问题在测试环境缺少 MyBatis-Plus 元数据。
- 未改成真实数据库集成测试：本类原本就是纯单元测试，扩大成本不必要。
- 未删除断言：保留现有权限、缓存、日志校验。

## 6. 修改过的 Java 测试文件

- `java/hello-demo/src/test/java/com/example/hello_demo/service/TicketServiceCacheSecurityTest.java`
- `java/hello-demo/src/test/java/com/example/hello_demo/controller/TicketPermissionIntegrationTest.java`

## 7. 是否修改业务代码

本轮没有为了修复 lambda-cache 修改 Java 业务代码。

## 8. Java 测试结果

通过：

```bash
.\mvnw.cmd "-Dtest=TicketServiceCacheSecurityTest" test
.\mvnw.cmd "-Dtest=TicketPermissionIntegrationTest" test
.\mvnw.cmd "-Dtest=TicketServiceCacheSecurityTest,TicketPermissionIntegrationTest" test
.\mvnw.cmd test
```

全量 Java 结果：104 tests, 0 failures, 0 errors。

## 9. Python 兼容代码清理列表

已清理旧的“直接保存 AI 回复”路径：

- `JavaTicketClient.save_ai_reply(...)`
- `AiReplySaveRequest`
- `tests/test_java_ticket_client.py` 中对应测试
- `tests/conftest.py` 中 FakeJavaTicketClient 的同名方法

## 10. 已删除的 Python 文件或代码

未删除整个文件，只删除上述方法、schema 和测试片段。

## 11. 未删除但疑似废弃的 Python 兼容路径

暂不删除：

- `AgentStateService`
- `pending_action_store`
- LangChain tool wrappers
- `AgentToolService` 中未读取的 `llm_service` 注入

原因：仍被测试、route 或工具注册路径引用，适合后续独立清理。

## 12. Python 验证结果

通过：

```bash
python -m compileall app
.\.venv\Scripts\python.exe -m pytest tests/test_java_ticket_client.py
.\.venv\Scripts\python.exe -m pytest
```

结果：

- compileall 通过
- `test_java_ticket_client.py`：26 passed
- Python 全量：243 passed, 1 warning

说明：直接用系统 `python -m pytest` 会因 Anaconda 环境缺少 `httpx` 失败；改用项目 `.venv` 后通过。

## 13. 前端 operation-type 对齐内容

`frontend/src/types/domain.ts` 新增 `operationTypeOptions`，并让 `OperationType` 从该数组推导。

补齐 Java `OperationType` 中已有但前端缺失的值：

- `AI_REPLY_SAVE_PENDING_CREATED`
- `AI_REPLY_SAVED`
- `AI_CATEGORY_APPLY_PENDING_CREATED`
- `AI_CATEGORY_APPLIED`
- `AI_ACTION_CANCELLED`
- `AI_ACTION_CONFIRM_FAILED`

`LogsPage.tsx` 的 actionType 下拉改为复用 `operationTypeOptions`。

## 14. Java actionType 来源

Java 来源：

```text
java/hello-demo/src/main/java/com/example/hello_demo/enums/OperationType.java
```

前端常量按该 enum 对齐。

## 15. 前端 build / lint 结果

通过：

```bash
npm.cmd run lint
npm.cmd run build
```

lint 结果：0 errors，1 个既有 `react-refresh/only-export-components` warning。

## 16. /tickets/{id}/detail 新增集成测试说明

新增测试：

```text
ticketDetailReturnsReplyContractInCreatedAtOrder
```

位置：

```text
java/hello-demo/src/test/java/com/example/hello_demo/controller/TicketPermissionIntegrationTest.java
```

## 17. 集成测试断言内容

断言：

- HTTP 200
- `$.code == 200`
- `$.data.replies[0].authorName`
- `$.data.replies[0].authorRole`
- `$.data.replies[0].replyType`
- `$.data.replies[0].createdAt`
- 第二条回复字段存在
- 两条回复按测试 fixture 的 `createdAt` 升序返回

## 18. 集成测试运行结果

`TicketPermissionIntegrationTest` 通过：4 tests, 0 failures, 0 errors。

## 19. 是否执行生产 Compose 验收

已执行。

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
```

容器状态：

- `ticket-frontend` Up，端口 `8088->80`
- `ticket-java-backend` Up
- `ticket-python-ai` Up
- `ticket-mysql` Up healthy
- `ticket-redis` Up healthy

## 20. 生产 Compose 验收结果

已验证：

- 前端 `/` 返回 HTTP 200
- `/api` 经 Nginx 代理到 Java
- tom/staff/admin 登录成功
- tom 只能看到自己的 acceptance 工单
- tom 不能访问工单日志，HTTP 403
- admin 可访问 operation logs
- admin 可访问 dashboard stats
- `/tickets/{id}/detail` 返回 replies，包含 `authorName`、`authorRole`、`replyType`
- AI 查询工单返回 200
- AI 创建工单进入 pending
- AI 创建工单确认后创建正式工单
- AI 创建工单取消后不能再确认
- AI 修改状态进入 pending
- AI 修改状态确认后更新为 `PROCESSING`
- AI 修改状态取消后状态保持不变
- STAFF 可生成 AI 回复建议
- 直接 POST `/tickets/{id}/ai-replies` 被拒绝，HTTP 400
- 保存 AI 回复先进入 pending
- 确认后 detail 中出现正式 AI 回复
- 取消后不保存回复
- 抽样 operation logs 响应中未发现 token/password

注意：PowerShell 发送中文 JSON 时需要用 UTF-8 bytes，否则 AI 意图抽取会误判缺少字段。

## 21. 未执行项

无。Docker 可用，已执行 Compose build、启动和脚本化验收。

## 22. 是否新增依赖

否。

## 23. 是否修改数据库表

否。

## 24. 是否影响现有接口

未改变 Java 接口结构。

Python 删除的是旧的直接保存 AI 回复客户端方法；正式流程仍是 Java-owned `pending_action`。

## 25. 风险提示

- 当前工作树已有大量未提交改动，本轮只在目标文件上做小改动。
- Python 仍有疑似 legacy 兼容组件，建议后续单独做删除。
- Compose 验收会在现有 MySQL volume 上新增验收数据；这符合本轮验收，但不是干净库回放。

## 26. 后续建议

- 将 Python legacy pending store 和 LangChain tool 外壳拆成单独清理任务。
- 给 Compose 验收脚本沉淀成 `scripts/acceptance`，避免每次手写 PowerShell。
- 如需减少 Java 测试日志噪声，可单独处理 Spring Boot debug/Mockito agent warning。

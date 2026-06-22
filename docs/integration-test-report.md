# 最终端到端验收报告

报告日期：2026-06-22  
项目根目录：`D:\code\AI-Enhanced Ticket Management System`

## 1. 当前架构结论

当前系统包含三端：

```text
frontend
java/hello-demo
ticket-agent-python
```

真实 AI 主链路：

```text
前端 / Postman
-> Java /ai/chat
-> Python /agent/chat
-> Java Ticket API
-> MySQL / Redis
```

关键结论：

- 前端生产请求只走 Java `/ai/chat`，不直连 Python `/agent/chat`。
- Java 负责 JWT、权限、TicketService、OperationLog、Redis 缓存和 pending_action。
- Python 负责意图识别、AI 编排、结构化回复、grounding 防幻觉和调用 Java API。
- 写操作不在 Python 直接执行，必须先创建 Java `ai_pending_action`，确认后由 Java 执行业务。

## 2. pending_action 当前状态

当前生产方案：

```text
存储位置：Java MySQL 表 ai_pending_action
隔离方式：当前登录 userId + conversationId
过期时间：10 分钟
状态：PENDING / CONFIRMED / CANCELLED / EXPIRED
动作：CREATE_TICKET / UPDATE_TICKET_STATUS / SAVE_AI_REPLY
```

安全规则：

- `payload_json` 不保存 token。
- 创建 pending_action 时 userId 来自当前 Java 登录上下文。
- 确认/取消时重新读取当前请求 Authorization。
- 确认时校验 pending_action owner。
- 重复确认不能重复执行业务写操作。

Python 中的 `InMemoryPendingActionStoreForTest` 只用于本地学习和测试兼容。

## 3. 配置核对

### Java

文件：

```text
java/hello-demo/src/main/resources/application.properties
```

当前关键配置：

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://127.0.0.1:3306/springboot_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
ai.service.base-url=${AI_SERVICE_BASE_URL:http://127.0.0.1:8001}
jwt.secret=${JWT_SECRET:dev-secret-change-me-dev-secret-change-me}
jwt.expiration=${JWT_EXPIRATION:86400000}
```

说明：

- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`JWT_EXPIRATION`、`AI_SERVICE_BASE_URL`、`REDIS_HOST`、`REDIS_PORT` 已支持环境变量。
- 生产环境必须覆盖 `JWT_SECRET` 和数据库密码。

### Python

文件：

```text
ticket-agent-python/app/core/config.py
ticket-agent-python/.env.example
```

关键变量：

```text
JAVA_API_BASE_URL=http://127.0.0.1:8080
JAVA_API_TOKEN=
JAVA_API_TIMEOUT=10
LLM_MOCK_MODE=true
LLM_API_KEY=
LLM_API_BASE_URL=
LLM_MODEL=
```

### 前端

文件：

```text
frontend/src/api/http.ts
frontend/vite.config.ts
```

关键配置：

```text
VITE_API_BASE_URL=/api
VITE_MOCK_API=false
```

Vite proxy：

```text
/api -> http://127.0.0.1:8080
```

## 4. 测试账号和数据

固定 SQL：

```text
docs/stage5-test-data.sql
```

账号：

| username | password | role | 用途 |
|---|---|---|---|
| `tom` | `123456` | USER | 普通用户，只能看自己的工单 |
| `alice` | `123456` | USER | 权限隔离验证 |
| `staff` | `123456` | STAFF | 工单处理和 AI 写操作确认 |
| `admin` | `123456` | ADMIN | 操作日志和管理验收 |

固定工单：

| title | owner | status | 用途 |
|---|---|---|---|
| `验收-登录失败` | tom | OPEN | 查询、详情、AI 回复建议、状态修改 |
| `验收-文件上传失败` | alice | OPEN | 验证 tom 不能越权访问 |

## 5. 本次实际执行命令

### 5.1 Java

命令：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\java\hello-demo"
.\mvnw.cmd test
```

结果：

```text
Tests run: 57, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

备注：

- 输出中有 Mockito / ByteBuddy 动态 agent 的未来 JDK warning。
- 不影响当前测试结果。

### 5.2 Python

命令：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\ticket-agent-python"
.\.venv\Scripts\python.exe -m pytest
```

结果：

```text
189 passed, 1 warning in 0.75s
```

备注：

- warning 来自 `fastapi.testclient` / Starlette 对 `httpx` 的弃用提示。
- 不影响当前测试结果。

### 5.3 前端

命令：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\frontend"
npm.cmd run build
```

结果：

```text
tsc -b && vite build
1655 modules transformed
built in 22.20s
```

## 6. 轻量环境检查

### 6.1 前端直连 Python 检查

检查命令：

```powershell
rg -n "localhost:8001|127\.0\.0\.1:8001|/agent/chat" frontend\src frontend\vite.config.ts frontend\package.json
```

结果：

```text
未发现匹配项
```

结论：前端源码未发现生产直连 Python `/agent/chat`。

### 6.2 MySQL

命令：

```powershell
Test-NetConnection 127.0.0.1 -Port 3306
```

结果：

```text
TcpTestSucceeded: True
```

### 6.3 Redis

命令：

```powershell
Test-NetConnection 127.0.0.1 -Port 6379
```

结果：

```text
TcpTestSucceeded: False
```

结论：本次自动化验证时 Redis 未启动或未监听 6379。完整手工联调前需要启动 Redis。

## 7. 已通过验收项

自动化已通过：

- Java JWT、权限、异常、AI pending_action、状态流转、缓存安全相关测试。
- Python JavaTicketClient、IntentRecognizer、pending_action、回复建议、grounding、新 AI 能力相关测试。
- 前端 TypeScript 编译和生产构建。
- 前端源码未发现直连 Python `/agent/chat` 的生产请求。

文档已同步：

- 总 README 启动顺序、配置、账号、pending_action 现状。
- Postman 最终验收顺序。
- 前端 AI 页面验收清单。
- 固定测试 SQL。

## 8. 未完成的手工联调

本次没有启动完整 Java + Python + 前端服务并跑 Postman 全链路，因为：

- Redis 6379 当前未连接成功。
- 未在本次执行中导入 `docs/stage5-test-data.sql` 到真实数据库。
- 未执行浏览器登录和 Network 面板检查。
- 未执行 Postman 的真实 token、真实数据库写入验证。

需要按以下文档继续手工验收：

```text
README.md
docs/postman-stage5-guide.md
docs/frontend-ai-checklist.md
```

## 9. 最终结论

当前代码级和构建级验收通过：

```text
Java mvnw test: 通过
Python pytest: 通过
Frontend npm build: 通过
```

完整端到端手工验收尚未宣称通过，必须在 Redis、Java、Python、前端全部启动后，按 Postman 和前端清单执行。

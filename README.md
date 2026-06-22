# AI-Enhanced Ticket Management System

本仓库是一个 Java 后端 + Python AI 服务 + React 前端组成的智能工单管理系统。

```text
java/hello-demo          Java Spring Boot 工单后端
ticket-agent-python     Python FastAPI AI 服务
frontend                React/Vite 前端
docs                    联调、验收、学习文档
postman                 Postman collection 和本地环境
```

## 1. 当前系统架构

真实调用链如下：

```text
浏览器前端
-> Java /ai/chat
-> Python /agent/chat
-> Java Ticket API
-> MySQL / Redis
```

职责边界：

- 前端只请求 Java 后端，生产链路不直接请求 Python。
- Java 是登录、JWT、权限、工单业务、pending_action、审计日志的入口。
- Python 负责 AI 意图识别、工具编排、回复建议、摘要、优先级建议、分类建议、相似工单和 SLA 风险提醒。
- 查询类操作可以直接执行，但仍然经过 Java 权限校验。
- 创建工单、修改状态、保存 AI 回复建议都必须 Human-in-the-loop：先生成待确认动作，再由用户确认。

当前 `pending_action` 方案：

```text
Java 表：ai_pending_action
隔离维度：当前登录 userId + conversationId
状态：PENDING / CONFIRMED / CANCELLED / EXPIRED
动作：CREATE_TICKET / UPDATE_TICKET_STATUS / SAVE_AI_REPLY
```

Python 中的 `InMemoryPendingActionStoreForTest` 只保留给本地学习和旧测试兼容，不是生产状态来源。`pending_action` 中不保存 token；确认时必须使用当前请求携带的有效 Authorization。

## 2. 启动顺序

完整联调按这个顺序启动：

```text
1. 启动 MySQL
2. 启动 Redis
3. 初始化数据库表
4. 导入 docs/stage5-test-data.sql
5. 启动 Java 后端
6. 启动 Python AI 服务
7. 启动前端
8. 浏览器或 Postman 登录测试
```

### 2.1 MySQL

数据库名：

```text
springboot_demo
```

初始化 SQL 推荐顺序：

```text
java/hello-demo/src/main/resources/sql/user.sql
java/hello-demo/src/main/resources/sql/ticket.sql
java/hello-demo/src/main/resources/sql/ticket_reply.sql
java/hello-demo/src/main/resources/sql/operation_log.sql
java/hello-demo/src/main/resources/sql/ai_pending_action.sql
docs/stage5-test-data.sql
```

PowerShell 检查端口：

```powershell
Test-NetConnection 127.0.0.1 -Port 3306
```

### 2.2 Redis

当前 Java 配置默认读取 `localhost:6379`，也可以用环境变量覆盖：

```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

Docker 启动示例：

```powershell
docker run -d --name redis-study -p 6379:6379 redis:7
docker exec redis-study redis-cli ping
```

如果容器已存在：

```powershell
docker start redis-study
docker exec redis-study redis-cli ping
```

预期返回：

```text
PONG
```

### 2.3 Java 后端

开发环境变量示例，不要把真实密码或生产密钥写进仓库：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/springboot_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-db-password"
$env:JWT_SECRET="replace-with-at-least-32-bytes-secret"
$env:JWT_EXPIRATION="86400000"
$env:AI_SERVICE_BASE_URL="http://127.0.0.1:8001"
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
```

启动：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\java\hello-demo"
.\mvnw.cmd spring-boot:run
```

验证：

```http
GET http://127.0.0.1:8080/hello
```

Java 当前配置键：

| 配置 | 当前来源 | 说明 |
|---|---|---|
| `DB_URL` | 环境变量，默认本地 MySQL | 数据库连接 |
| `DB_USERNAME` | 环境变量，默认 `root` | 数据库用户名 |
| `DB_PASSWORD` | 环境变量，默认空 | 数据库密码 |
| `JWT_SECRET` | 环境变量，默认开发密钥 | 生产必须修改 |
| `JWT_EXPIRATION` | 环境变量，默认 `86400000` | JWT 过期时间，毫秒 |
| `AI_SERVICE_BASE_URL` | 环境变量，默认 `http://127.0.0.1:8001` | Java 调 Python 地址 |
| `REDIS_HOST` | 环境变量，默认 `localhost` | Redis 地址 |
| `REDIS_PORT` | 环境变量，默认 `6379` | Redis 端口 |

### 2.4 Python AI 服务

安装依赖：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\ticket-agent-python"
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

`.env` 示例：

```text
JAVA_API_BASE_URL=http://127.0.0.1:8080
JAVA_API_TOKEN=
JAVA_API_TIMEOUT=10

LLM_MOCK_MODE=true
LLM_API_KEY=
LLM_API_BASE_URL=
LLM_MODEL=
```

启动：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\ticket-agent-python"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

验证：

```http
GET http://127.0.0.1:8001/health
```

如果改成 8002，必须同步 Java：

```powershell
$env:AI_SERVICE_BASE_URL="http://127.0.0.1:8002"
```

### 2.5 前端

前端默认通过 Vite proxy 请求 Java：

```text
浏览器 -> /api -> http://127.0.0.1:8080
```

启动：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\frontend"
npm.cmd install
npm.cmd run dev
```

可选环境变量：

```powershell
$env:VITE_API_BASE_URL="/api"
$env:VITE_MOCK_API="false"
```

本地 UI mock：

```powershell
$env:VITE_MOCK_API="true"
npm.cmd run dev
```

## 3. 固定测试账号

`docs/stage5-test-data.sql` 会准备以下账号，登录明文密码都是 `123456`。

| username | password | role | 验收用途 |
|---|---|---|---|
| `tom` | `123456` | USER | 普通用户，只能看自己的工单，不能修改状态，不能看日志 |
| `alice` | `123456` | USER | 普通用户，用于验证不能访问 tom 的工单 |
| `staff` | `123456` | STAFF | 工作人员，可以处理工单、修改允许状态、保存 AI 回复建议 |
| `admin` | `123456` | ADMIN | 管理员，可以查看更完整日志和管理数据 |

登录接口：

```http
POST http://127.0.0.1:8080/auth/login
Content-Type: application/json
```

```json
{
  "username": "tom",
  "password": "123456"
}
```

## 4. Postman 验收

导入：

```text
postman/stage5-full-integration.postman_collection.json
postman/stage5-local.postman_environment.json
```

详细顺序见：

```text
docs/postman-stage5-guide.md
```

推荐主线：

```text
1. 登录 tom，保存 token
2. tom 查询自己的工单
3. tom 尝试看他人工单，预期失败
4. tom 尝试修改状态，预期失败
5. 登录 staff，保存 token
6. staff 使用 AI 查询工单
7. staff 使用 AI 创建工单，预期 pending
8. staff 确认创建，预期数据库新增
9. staff 再次确认，预期不会重复新增
10. staff 使用 AI 修改状态，预期 pending
11. staff 确认修改，预期状态变化
12. staff 取消某个写操作，预期不执行
13. staff 生成 AI 回复建议，预期返回 JSON
14. staff 保存 AI 回复建议，预期 TicketReplyType.AI
15. token 错误或过期，预期 401
```

## 5. 前端页面验收

清单见：

```text
docs/frontend-ai-checklist.md
```

关键点：

- AI 页面请求只能打到 Java `/ai/chat`。
- Confirm / Cancel 必须复用同一个 `conversationId`。
- 回复建议要显示 `suggestion`、`confidence`、`reason`、`risk_flags`。
- 401 或 token 失效后前端要清登录态并回到登录流程。

## 6. 自动化测试与手工联调

自动化测试：

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\java\hello-demo"
.\mvnw.cmd test
```

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\ticket-agent-python"
.\.venv\Scripts\python.exe -m pytest
```

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\frontend"
npm.cmd run build
```

手工联调：

- 浏览器登录 tom / staff / admin。
- 前端 AI 页面查询、创建、确认、取消、修改状态、回复建议。
- Postman 按顺序调用接口。
- MySQL 检查 `ticket`、`ticket_reply`、`ai_pending_action`、`operation_log`。
- Redis 检查缓存行为。
- 浏览器 Network 检查前端只请求 Java。

为什么两者都需要：

- 自动化测试检查权限、状态流转、pending_action 幂等、防幻觉、JSON 解析等规则。
- 手工联调检查真实 MySQL、Redis、Java、Python、前端、JWT 和网络配置是否连通。

## 7. 常见问题

### 7.1 401 Token 格式错误

前端和 Postman 只保存 token 字符串即可；发请求时放在 Header：

```http
Authorization: Bearer <token>
```

不要把 token 放进 body，也不要把完整 token 写进日志或文档。

### 7.2 Python 端口变化

如果 Python 从 8001 改成 8002，Java 必须同步：

```powershell
$env:AI_SERVICE_BASE_URL="http://127.0.0.1:8002"
```

然后重启 Java。

### 7.3 Redis 没启动

工单详情缓存依赖 Redis。先确认：

```powershell
docker exec redis-study redis-cli ping
```

### 7.4 生产安全

生产环境必须修改：

- `DB_PASSWORD`
- `JWT_SECRET`
- `LLM_API_KEY`
- GitHub、OpenAI、数据库等所有真实凭证

不要提交 `.env`、真实 token、真实 API Key。

# 部署与测试闭环指南

本文档说明如何在本地和 Docker Compose 中启动智能工单系统，并完成基础验收。

## 1. 系统链路

```text
Frontend Nginx
-> /api
-> Java Spring Boot
-> Python FastAPI AI
-> Java Ticket API
-> MySQL / Redis
```

约束：

- 前端只请求 `/api`，不直连 Python。
- Java 负责认证、权限、工单业务、pending_action 和审计。
- Python 只做意图识别、AI 能力编排和 Java API 调用。
- 写操作必须先生成 pending_action，再由用户确认。

## 2. 本地开发启动

### 2.1 MySQL

数据库名默认是 `springboot_demo`。初始化 SQL 推荐顺序：

```text
java/hello-demo/src/main/resources/sql/user.sql
java/hello-demo/src/main/resources/sql/ticket.sql
java/hello-demo/src/main/resources/sql/ticket_reply.sql
java/hello-demo/src/main/resources/sql/operation_log.sql
java/hello-demo/src/main/resources/sql/ai_pending_action.sql
docs/stage5-test-data.sql
```

如果是已有数据库，请额外执行：

```text
docs/migrations/add_ticket_category_assigned_to.sql
```

说明：Docker MySQL volume 已存在时，不会自动重新执行初始化 SQL。旧库需要手动执行 migration，确保 `ticket.category` 为 `VARCHAR(64) NULL`，并新增 `ticket.assigned_to`。

### 2.2 Redis

```powershell
docker run -d --name redis-study -p 6379:6379 redis:7
docker exec redis-study redis-cli ping
```

预期返回：

```text
PONG
```

### 2.3 Java 后端

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\java\hello-demo"
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/springboot_demo?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your-db-password"
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
$env:JWT_SECRET="replace-with-at-least-32-bytes-secret"
$env:JWT_EXPIRATION="86400000"
$env:AI_SERVICE_BASE_URL="http://127.0.0.1:8001"
.\mvnw.cmd spring-boot:run
```

### 2.4 Python AI 服务

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\ticket-agent-python"
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:JAVA_API_BASE_URL="http://127.0.0.1:8080"
$env:LLM_MOCK_MODE="true"
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

### 2.5 前端

```powershell
cd "D:\code\AI-Enhanced Ticket Management System\frontend"
npm.cmd install
$env:VITE_API_BASE_URL="/api"
npm.cmd run dev
```

## 3. Docker Compose 启动

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

修改 `.env` 中的示例值，至少修改：

```text
DB_PASSWORD
JWT_SECRET
LLM_API_KEY
LLM_BASE_URL
LLM_MODEL
```

如果暂时只想验证无真实 LLM 的链路，可保留：

```text
LLM_MOCK_MODE=true
```

启动：

```powershell
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

本地 Docker 部署默认访问：

```text
http://localhost:8088
```

查看容器：

```powershell
docker compose -f docker-compose.prod.yml ps
```

查看日志：

```powershell
docker compose -f docker-compose.prod.yml logs -f java-backend
docker compose -f docker-compose.prod.yml logs -f python-ai
docker compose -f docker-compose.prod.yml logs -f frontend
```

停止：

```powershell
docker compose -f docker-compose.prod.yml down
```

停止并删除数据卷：

```powershell
docker compose -f docker-compose.prod.yml down -v
```

## 4. 环境变量说明

| 变量 | 服务 | 说明 |
|---|---|---|
| `DB_NAME` | MySQL / Java | 数据库名 |
| `DB_USERNAME` | Java | 数据库用户名 |
| `DB_PASSWORD` | MySQL / Java | 数据库密码 |
| `REDIS_DATABASE` | Java | Redis database index |
| `JWT_SECRET` | Java | JWT 签名密钥，生产必须改成长随机值 |
| `JWT_EXPIRATION` | Java | JWT 过期时间，毫秒 |
| `AI_SERVICE_BASE_URL` | Java | Java 调 Python 地址，Docker 中为 `http://python-ai:8001` |
| `JAVA_API_BASE_URL` | Python | Python 调 Java 地址，Docker 中为 `http://java-backend:8080` |
| `JAVA_API_TIMEOUT` | Python | Python 调 Java 超时时间 |
| `LLM_MOCK_MODE` | Python | 是否启用 LLM mock |
| `LLM_API_KEY` | Python | LLM API Key，不要提交真实值 |
| `LLM_BASE_URL` / `LLM_API_BASE_URL` | Python | OpenAI-compatible API base URL |
| `LLM_MODEL` | Python | 使用的模型名 |
| `VITE_API_BASE_URL` | 前端构建 | 前端请求入口，Docker 中固定为 `/api` |
| `FRONTEND_PORT` | 前端 Docker | 前端暴露到宿主机的端口，本地默认 `8088`；服务器需要 80 时可改为 `80` |

## 5. 验证链路

### 5.1 验证前端只请求 Java

打开浏览器开发者工具 Network：

- 预期请求路径是 `/api/auth/login`、`/api/ai/chat`。
- 不应出现 `localhost:8001` 或 `/agent/chat`。

### 5.2 验证 Java 调 Python

使用 staff 登录后在 AI 页面发送：

```text
查询我的工单
```

查看 Java 日志：

```powershell
docker compose -f docker-compose.prod.yml logs -f java-backend
```

应看到 Java 转发 AI chat 请求到 Python。

### 5.3 验证 Python 调 Java

发送：

```text
总结 1 号工单
```

查看 Python 日志：

```powershell
docker compose -f docker-compose.prod.yml logs -f python-ai
```

应看到 Python 调 Java 工单详情或查询接口。

### 5.4 验证 pending_action

发送：

```text
帮我创建一个高优先级工单，标题是测试工单，描述是部署验收
```

预期：

- 前端显示 Confirm / Cancel。
- 确认后 Java 数据库新增工单。
- 再次确认不会重复新增。

## 6. 自动化测试

前端：

```powershell
cd frontend
npm.cmd run test
npm.cmd run build
```

Java：

```powershell
cd java/hello-demo
.\mvnw.cmd test
```

Python：

```powershell
cd ticket-agent-python
.\.venv\Scripts\python.exe -m pytest
```

Docker 配置校验：

```powershell
docker compose -f docker-compose.prod.yml --env-file .env config
```

## 7. MySQL 备份

```powershell
docker exec ticket-mysql mysqldump -uroot -p%DB_PASSWORD% springboot_demo > backup.sql
```

PowerShell 中如果 `%DB_PASSWORD%` 不生效，直接输入密码或改用：

```powershell
docker exec ticket-mysql mysqldump -uroot -p springboot_demo > backup.sql
```

## 8. 常见问题

### Java 连不上 MySQL

检查：

- `.env` 中 `DB_PASSWORD` 是否和 MySQL root 密码一致。
- `DB_URL` 是否指向 `mysql:3306`。
- MySQL 容器是否 healthy。

### Java 连不上 Python

检查：

- Java 环境变量 `AI_SERVICE_BASE_URL=http://python-ai:8001`。
- `python-ai` 容器是否运行。
- Python 日志是否启动 Uvicorn。

### Python 连不上 Java

检查：

- Python 环境变量 `JAVA_API_BASE_URL=http://java-backend:8080`。
- Java 容器是否启动完成。
- Java 日志是否有 MySQL 或 Redis 连接失败。

### 前端请求 localhost

生产构建中前端应使用：

```text
VITE_API_BASE_URL=/api
```

如果 Network 中出现 `localhost:8001`，说明前端直连了 Python，需要回到 `frontend/src/api/client.ts` 检查 API 封装。

### MySQL 初始化 SQL 没执行

MySQL 官方镜像只会在数据目录为空时执行 `/docker-entrypoint-initdb.d`。如果已经启动过，需要重建数据卷：

```powershell
docker compose -f docker-compose.prod.yml down -v
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

### 端口被占用

当前只暴露前端，默认使用宿主机 `8088` 端口：

```env
FRONTEND_PORT=8088
```

如果服务器要直接使用 80，可以在 `.env` 中设置：

```env
FRONTEND_PORT=80
```

如果 80 被占用，继续使用 8088，或改成其他未被占用端口：

```env
FRONTEND_PORT=8089
```

然后访问：

```text
http://127.0.0.1:8088
```

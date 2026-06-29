# Project Audit Report

## 1. 项目模块划分

| 模块 | 路径 | 主要职责 |
| --- | --- | --- |
| 前端 | `frontend/` | React/Vite 页面、登录态、角色导航、工单列表/详情、AI 助手入口。 |
| Java 后端 | `java/hello-demo/` | Spring Boot API、JWT 认证、工单/回复/用户/日志、AI 转发、pending_action。 |
| Python AI Agent | `ticket-agent-python/` | FastAPI `/agent/chat`、意图识别、工单查询、AI 分析、写操作待确认编排。 |
| 文档 | `README.md`, `docs/`, 各模块 `README.md` | 本地运行、部署、接口、阶段学习和验收记录。 |
| 部署 | `docker-compose.prod.yml`, `frontend/nginx.conf`, 各 Dockerfile | MySQL、Redis、Java、Python、前端 Nginx 的生产式组合部署。 |
| 测试 | `frontend/src/**/*.test.*`, `java/hello-demo/src/test/`, `ticket-agent-python/tests/` | 前端组件/API、Java 权限/AI/pending_action、Python Agent 行为。 |

## 2. 当前启动方式

### Docker 生产式启动

```powershell
copy .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

前端入口默认是 `http://localhost:8088`。Nginx 将 `/api/` 代理到 compose 网络内的 `java-backend:8080`。

### 分模块本地启动

```powershell
cd java\hello-demo
.\mvnw.cmd spring-boot:run
```

```powershell
cd ticket-agent-python
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

## 3. 关键接口

| 层 | 接口 | 说明 |
| --- | --- | --- |
| Java | `POST /auth/login`, `GET /auth/me` | 登录和当前用户。 |
| Java | `GET /tickets`, `GET /tickets/{id}/detail` | 工单列表和详情。 |
| Java | `POST /tickets`, `PUT /tickets/{id}/status` | 工单写操作，后端从 JWT 取用户。 |
| Java | `POST /tickets/{id}/replies` | 普通回复。 |
| Java | `POST /ai/chat` | 前端唯一 AI 对话入口，Java 转发到 Python。 |
| Java | `POST /ai/pending-actions/*` | AI 写操作确认/取消。 |
| Java | `GET /operation-logs`, `GET /tickets/{id}/logs` | 操作日志。 |
| Python | `POST /agent/chat` | Java 调用的 Agent 对话入口。 |
| Python | `POST /ai/tickets/{id}/reply-suggestion` | 按真实工单上下文生成 AI 回复建议。 |

## 4. 当前检查结论

- 前端生产请求统一经 `frontend/src/api/http.ts` 走 `/api`，由 Nginx 转给 Java；未发现生产前端直连 Python。
- Java `/ai/chat` 会从 `CurrentUserContext` 取 `userId`，从请求头取 Authorization，并转发给 Python。
- Python 写操作创建 Java `pending_action`，确认/取消再回调 Java；查询类操作直接走 Java 权限校验后的只读接口。
- `mock` 代码仍被前端测试、Python 测试、本地 mock 模式使用，不应删除。

## 5. 风险点

| 风险 | 影响 | 建议 |
| --- | --- | --- |
| Docker Desktop 未启动 | 本次无法完成真实 compose 验收。 | 启动 Docker 后执行 `docker compose -f docker-compose.prod.yml up -d --build`。 |
| 前端 lint 有 2 个 Fast Refresh warning | 不影响构建，但会保持 lint 输出不干净。 | 后续可把非组件导出移到独立文件。 |
| 前端测试有 React `act(...)` warning | 测试通过，但异步断言可读性差。 | 后续只改测试等待方式，不改业务。 |
| Java 默认 dev JWT 密钥 | 本地安全可接受，生产必须覆盖。 | 生产 `.env` 必填 `JWT_SECRET`。 |
| Python 系统解释器缺依赖 | 直接 `python -m pytest` 会失败。 | 使用 `ticket-agent-python/.venv/Scripts/python.exe` 或先安装 requirements。 |


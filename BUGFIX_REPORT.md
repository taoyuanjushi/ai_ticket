# Bugfix Report

## 1. 发现的问题

| 问题 | 影响 | 处理 |
| --- | --- | --- |
| 根目录空 `package-lock.json` | 容易让人误以为根目录是 npm 项目，提交/安装路径混乱。 | 已删除。 |
| Java 源码目录内存在 `example.lnk` | Windows 快捷方式混入源码，干扰源码审计和打包认知。 | 已删除，并在 `.gitignore` 加 `*.lnk`。 |
| Docker Desktop 未运行 | 无法执行完整 compose 验收。 | 已记录，未改代码。 |
| 系统 Python 缺 `httpx` | 直接 `python -m pytest` 失败。 | 改用项目 `.venv` 验证通过。 |

## 2. 本次没有修改的核心代码

未发现需要立即改动的确定运行时 bug，因此没有改前端业务、Java 业务或 Python Agent 业务代码。当前检查到的权限、AI 转发、pending_action 方向都与目标一致。

## 3. 修改的文件

- `.gitignore`
- `package-lock.json`，已删除
- `java/hello-demo/src/main/java/com/example/hello_demo/example.lnk`，已删除
- `PROJECT_AUDIT_REPORT.md`
- `CLEANUP_REPORT.md`
- `BUGFIX_REPORT.md`
- `NEXT_STAGE_PLAN.md`
- `LEARNING_GUIDE.md`

## 4. 验证结果

| 命令 | 结果 |
| --- | --- |
| `docker compose -f docker-compose.prod.yml config --quiet` | 通过。 |
| `npm.cmd run lint`，在 `frontend/` | 通过，保留 2 个 Fast Refresh warning。 |
| `npm.cmd run test`，在 `frontend/` | 6 个测试文件、42 个测试通过；有既有 React Router/act warning。 |
| `npm.cmd run build`，在 `frontend/` | 通过。 |
| `.\mvnw.cmd test`，在 `java/hello-demo/` | 93 个测试通过。 |
| `python -m pytest`，在 `ticket-agent-python/` | 失败：系统 Python 缺 `httpx`。 |
| `.\.venv\Scripts\python.exe -m pytest`，在 `ticket-agent-python/` | 244 个测试通过，1 个 StarletteDeprecationWarning。 |
| `docker compose -f docker-compose.prod.yml up -d --build` | 失败：Docker Desktop daemon 未运行。 |

## 5. 仍未解决的问题

1. Docker Desktop 启动前无法做真实浏览器/compose 验收。
2. 前端测试 warning 可以后续清理，但不是本次 bug。
3. 系统 Python 环境缺依赖，建议统一使用项目 `.venv`。


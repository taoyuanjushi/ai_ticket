# Cleanup Report

## 1. 已删除文件

| 文件 | 删除原因 |
| --- | --- |
| `package-lock.json` | 根目录没有 `package.json`，该 lock 文件只有空 `packages`，真实前端依赖锁定在 `frontend/package-lock.json`。 |
| `java/hello-demo/src/main/java/com/example/hello_demo/example.lnk` | Windows 快捷方式混入 Java 源码目录，不参与编译、运行、测试或部署。 |

## 2. 删除前如何确认无引用

- 全局搜索 `package-lock.json`，文档中的安装命令均指向 `frontend/npm.cmd install`，没有根目录 npm 项目。
- 检查根目录：存在 `package-lock.json`，不存在 `package.json`。
- 全局搜索 `example.lnk`，没有代码、测试、Docker 或文档引用。
- `example.lnk` 是二进制快捷方式，不属于 Java 源码。

## 3. 同步修改

- `.gitignore` 增加 `*.lnk`，避免 Windows 快捷方式再次进入仓库。

## 4. 未删除但疑似废弃或需后续确认

| 文件/区域 | 当前判断 |
| --- | --- |
| `frontend/src/api/mock.ts` | 不删除。前端测试和本地 mock 模式仍依赖。 |
| `ticket-agent-python/app/tools/mock_ticket_data.py` | 不删除。Python 测试仍引用。 |
| `ticket-agent-python/app/services/pending_action_store.py` | 不删除。标注为兼容旧测试/本地 demo，测试仍覆盖。 |
| `java/hello-demo/src/main/java/com/example/hello_demo/controller/HelloController.java` | 疑似学习期接口，但未确认是否仍被演示使用，暂不删。 |
| 文档中的 `127.0.0.1:8001` 示例 | 多数是本地调试文档，不是生产代码，暂不删。 |

## 5. 后续建议

1. 启动 Docker 后补一次真实 compose 验收。
2. 后续若要继续删旧功能，先把 `HelloController`、旧兼容方法、旧文档示例列成单独清单逐项确认。
3. 保留 mock 直到有明确替代测试数据层。


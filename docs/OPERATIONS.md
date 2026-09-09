# Project Sentinel 运行手册

## 运行边界

Project Sentinel 是单机、单用户工具。Web 服务只监听回环地址，不应通过端口转发、反向代理或容器端口映射暴露到局域网或互联网。基础扫描与本地 RAG 不联网；只有显式配置模型环境变量后，增强 RAG 和 Function Calling Agent 才会访问模型端点。

## 上线前检查

1. 安装 Java 21，并运行 `java -version` 确认实际版本。
2. 从 Release ZIP 解压到独立目录，不要只复制 JAR；启动器和文档属于发行契约的一部分。
3. Windows 运行 `project-sentinel.cmd --version`，Linux/macOS 运行 `./project-sentinel.sh --version`。
4. 对目标项目运行 `project-sentinel.cmd --check-json <项目>`，确认退出码为 0 且 JSON 可解析。
5. 启动 Web 后访问 `/api/health`，确认 `status` 为 `UP`、工作区和项目数量符合预期。

从 GitHub Release 下载时，除核对同名 `.sha256` 外，建议安装 GitHub CLI 后验证构件来源：

```powershell
gh attestation verify .\project-sentinel-0.3.1-distribution.zip --repo Dusk4d/project-sentinel
```

把示例版本替换为实际下载版本。验证成功表示 ZIP 摘要与本仓库标签发布工作流生成的签名来源证明一致；它不能替代运行环境安全检查。

## 启动与停止

Windows：

```powershell
.\start-web.cmd D:\path\to\workspace 8787
```

Linux/macOS：

```sh
./start-web.sh /path/to/workspace 8787
```

浏览器访问 `http://127.0.0.1:8787/`。按 `Ctrl+C` 或关闭启动终端停止服务。端口被占用、工作区不存在或模型配置无效时，进程以非零状态退出并打印原因。

## 可选模型配置

模型配置只通过进程环境传入，不写入项目或状态目录：

- `SENTINEL_MODEL_BASE_URL`：OpenAI Chat Completions 兼容服务地址；远程地址必须使用 HTTPS。
- `SENTINEL_MODEL_NAME`：服务支持的模型名。
- `SENTINEL_MODEL_API_KEY`：仅在服务要求鉴权时设置。

启动后通过 `/api/health` 的 `modelEnabled` 判断配置是否完整。该字段不代表远程服务一定可用；应在页面执行一次模型增强 RAG 和一次 Agent 分析作为连通性验收。不要把真实密钥写入 `.env`、启动脚本、Git、报告或问题截图。

## 每日任务与状态

生产式日检建议使用 `--daily-verify`，并把状态目录放在待扫描项目之外：

```powershell
.\project-sentinel.cmd --daily-verify D:\path\to\project D:\sentinel-state 80 120 5
```

参数依次为项目、状态目录、最低健康分、构建超时秒数和最大允许降幅。使用 `--state-status D:\sentinel-state` 查看最近完成清单和是否有任务正在运行。

建议备份状态目录中的 `history.tsv`、`latest-risk-baseline.properties`、`latest-run.json` 和需要保留的报告。备份时不要与日检并发复制；先用 `--state-status` 确认没有活动任务。状态文件损坏时不要手工猜测修复，先保留副本，再使用新的空状态目录建立新基线。

## 监控与故障定位

- `/api/health` 不通：确认启动终端仍在运行、端口正确且未被其他进程占用。
- 返回 403：确认请求的 `Host` 和 `Origin` 是当前回环地址及端口，不要从其他网站调用本机 API。
- 返回 429：已有扫描或 Agent 任务运行；读取 `Retry-After` 后重试。
- 返回 502/503：检查模型端点、模型名、网络和服务日志；本地扫描及普通 RAG 仍可独立使用。
- 日检退出码 3：质量门禁失败；查看 `latest.json`、`latest-plan.json` 和 `latest-run.json`。
- 日检退出码 4 或 5：构建失败或超时；查看状态目录中保存的完整构建日志。
- 日检退出码 6：相同状态目录已有进程持锁，不要删除锁文件来绕过并发保护。
- 带记忆 Agent 中断：运行 `--state-status <状态目录>`；若检查点为 `ACTIVE`，使用输出中的原任务重新执行 `--agent-ai-memory`，不要换任务覆盖。

## 升级与回滚

升级前备份状态目录并记录当前 `--version`。在新目录解压新版 ZIP，先运行版本、静态扫描和健康接口检查，再切换启动脚本。不要覆盖正在运行的发行目录。

回滚时停止当前服务，重新启动上一版完整发行目录。状态文件具有版本校验；若旧版本拒绝读取新版状态，使用升级前备份或新的状态目录，禁止通过删除字段强行降级。

## 数据与卸载

程序不会自动删除历史报告。卸载前先停止服务；发行目录和用户显式指定的状态目录可以分别归档。Project Sentinel 不需要系统服务、注册表项或全局数据库，删除发行目录不会自动删除外部状态目录，也不会删除被扫描项目。

# Workspace Agent

一个基于 Java 21 的本地项目健康检查与行动规划 Agent，面向学生和个人开发者。现实问题是：项目散落在本机，README、测试、构建配置和 TODO 状态常常无人持续维护，直到交付或面试前才暴露风险。本工具在不上传源码的前提下扫描项目，给出带文件证据的风险分级和下一步行动建议。

当前版本完全本地运行，不需要 API 密钥，也不会把代码发送给第三方。

## 运行

```powershell
mvn compile
mvn exec:java -Dexec.args="D:\path\to\workspace"
```

打包后也可直接运行：

```powershell
mvn package
java -jar target/workspace-agent-0.2.0.jar D:\path\to\workspace
```

进入交互界面后可输入：

- `列出文件`
- `读取 README.md`
- `搜索 TODO`
- `体检` 或 `体检 some-project`
- `帮助`
- `退出`

适合自动化任务的非交互命令：

```powershell
# 在控制台输出单项目体检
java -cp target/classes local.agent.Main --check D:\path\to\project

# 输出带版本号的机器可读 JSON，适合脚本、看板和 CI
java -jar target/workspace-agent-0.2.0.jar --check-json D:\path\to\project

# 输出按风险和预计恢复分值排序的行动计划
java -jar target/workspace-agent-0.2.0.jar --plan D:\path\to\project

# 以原子写入方式保存带时间戳的报告
java -cp target/classes local.agent.Main --report D:\path\to\project D:\path\to\reports

# 生成无需服务器、可直接用浏览器打开的单文件 HTML 看板
java -jar target/workspace-agent-0.2.0.jar --report-html D:\path\to\project D:\path\to\dashboard.html

# 扫描一个目录下可识别的多个项目，按健康分排序
java -cp target/classes local.agent.Main --portfolio D:\path\to\workspace

# 生成稳定的多项目 Markdown、HTML、JSON 看板，并以最低项目分执行门禁
java -jar target/workspace-agent-0.2.0.jar --portfolio-daily D:\path\to\workspace D:\path\to\portfolio-state 70

# 多项目模式默认向下发现 4 层目录中的构建清单或 Git 仓库

# 追加结构化健康快照，并展示跨日趋势
java -jar target/workspace-agent-0.2.0.jar --snapshot D:\path\to\project D:\path\to\history.tsv
java -jar target/workspace-agent-0.2.0.jar --trend D:\path\to\history.tsv

# 推荐给定时任务：一次完成报告、快照、趋势和质量门禁；最低分默认 70
java -jar target/workspace-agent-0.2.0.jar --daily D:\path\to\project D:\path\to\agent-state 80
```

使用 `--help` 查看完整命令，使用 `--version` 查看版本。未知选项或缺少参数会输出帮助并返回退出码 `2`。

`--daily` 退出码约定：`0` 表示通过，`1` 表示运行故障，`2` 表示参数错误，`3` 表示健康分低于门禁。该约定便于任务计划程序和 CI 可靠判断结果。

每次 `--daily` 会在状态目录中原子更新 `latest.html` 与 `latest.json`，追加 `history.tsv`，并在 `reports` 子目录保存带时间戳的 Markdown。历史报告不会被自动删除。

JSON 输出包含 `schemaVersion`。消费者应按版本解析字段，不依赖字段排列顺序。

运行标准自动化测试：

```powershell
mvn test
```

推荐使用固定版本 Wrapper 执行完整校验：

```powershell
.\mvnw.cmd clean verify
```

## 安全边界

- 只允许访问启动时指定的工作区。
- 拒绝绝对路径和任何穿越工作区的路径。
- 对现有文件解析真实路径，拒绝借助符号链接或目录联接逃逸工作区。
- 首版工具全部只读，不会修改或删除文件。
- 单次读取和搜索有结果数量与文件大小上限。
- 依赖锁定检查覆盖 Maven/Gradle Wrapper、Node 锁文件、Python 锁文件、Cargo.lock 和 go.sum。

## 项目级配置

在待分析项目根目录创建 `.workspace-agent.properties`：

```properties
ignore.directories=generated,coverage,reports
scan.maxFiles=10000
scan.maxTextBytes=262144
todo.warningThreshold=20
rules.disabled=legal.license,tests.ratio
score.high=25
score.medium=12
score.low=5
waiver.legal.license=2026-12-31|alice|等待组织确认许可证
```

忽略项只能是目录名，不能使用路径或 `..`。规则抑制使用报告中的稳定 `ruleId`，例如 `legal.license`；不要依赖中文文案。长期使用时优先采用 `waiver.<ruleId>=到期日|负责人|原因`：有效豁免保留发现但不扣分，到期后自动恢复扣分。评分权重限制为 0～100，且必须满足高风险 ≥ 中风险 ≥ 低风险。错误配置会令任务失败并返回退出码 `1`。

## 路线图

- 增加带审批策略的文件创建与补丁工具。
- 支持工作区级配置继承和项目覆盖。
- 增加任务历史、记忆和可恢复执行状态。
- 接入可选的本地模型或兼容 OpenAI 协议的模型。
- 增加更完善的自动化测试和打包发布流程。

产品背景见 [docs/VISION.md](docs/VISION.md)，设计边界见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，威胁模型见 [docs/SECURITY.md](docs/SECURITY.md)。

贡献约定见 [CONTRIBUTING.md](CONTRIBUTING.md)，版本变化见 [CHANGELOG.md](CHANGELOG.md)。GitHub Actions 配置只授予源码只读权限，并使用 Maven Wrapper 执行同一套验证。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。

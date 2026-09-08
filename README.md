# Project Sentinel（项目哨兵）

Project Sentinel 是一个基于 Java 21 的本地项目健康检查与行动规划 Agent，面向学生和个人开发者。现实问题是：项目散落在本机，README、测试、构建配置和 TODO 状态常常无人持续维护，直到交付或面试前才暴露风险。本工具在不上传源码的前提下扫描项目，给出带文件证据的风险分级和下一步行动建议。构件名和 Java 包暂时保留兼容名称 `workspace-agent`。

当前版本完全本地运行，不需要 API 密钥，也不会把代码发送给第三方。

## 运行

Windows 最快捷的方式是在资源管理器中双击 `start-web.cmd`。它默认扫描本项目的父目录、使用端口 8787，并在缺少 JAR 时自动调用 Maven Wrapper 构建。启动后访问 `http://127.0.0.1:8787/`，关闭窗口或按 `Ctrl+C` 停止。

也可以在 PowerShell 中指定工作区和端口：

```powershell
.\start-web.cmd D:\Desktop\Study\Project 8787
```

```powershell
.\mvnw.cmd compile
.\mvnw.cmd exec:java -Dexec.args="D:\path\to\workspace"
```

打包后也可直接运行：

```powershell
.\mvnw.cmd package
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

# 输出版本化的机器可读行动计划
java -jar target/workspace-agent-0.2.0.jar --plan-json D:\path\to\project

# 以原子写入方式保存带时间戳的报告
java -cp target/classes local.agent.Main --report D:\path\to\project D:\path\to\reports

# 生成无需服务器、可直接用浏览器打开的单文件 HTML 看板
java -jar target/workspace-agent-0.2.0.jar --report-html D:\path\to\project D:\path\to\dashboard.html

# 启动带项目选择和“重新扫描”按钮的本地 Web 后端与看板；默认端口 8787
java -jar target/workspace-agent-0.2.0.jar --serve D:\path\to\workspace 8787

# 扫描一个目录下可识别的多个项目，按健康分排序
java -cp target/classes local.agent.Main --portfolio D:\path\to\workspace

# 生成稳定的多项目 Markdown、HTML、JSON 看板，并以最低项目分执行门禁
java -jar target/workspace-agent-0.2.0.jar --portfolio-daily D:\path\to\workspace D:\path\to\portfolio-state 70

# 多项目模式默认向下发现 4 层目录中的构建清单或 Git 仓库

# 追加结构化健康快照，并展示跨日趋势
java -jar target/workspace-agent-0.2.0.jar --snapshot D:\path\to\project D:\path\to\history.tsv
java -jar target/workspace-agent-0.2.0.jar --trend D:\path\to\history.tsv

# 推荐给定时任务：一次完成报告、快照、趋势和质量门禁
# 末尾的 5 表示相比上次快照最多允许下降 5 分；省略时不限制降幅
java -jar target/workspace-agent-0.2.0.jar --daily D:\path\to\project D:\path\to\agent-state 80 5

# 显式执行项目测试；默认超时 120 秒，最大 1800 秒
java -jar target/workspace-agent-0.2.0.jar --verify-build D:\path\to\project 120

# 显式执行“静态日报 + 真实构建”，保存最新与历史构建证据
java -jar target/workspace-agent-0.2.0.jar --daily-verify D:\path\to\project D:\path\to\agent-state 80 120 5

# 查询状态目录当前是否被任务占用，并显示进程、开始时间和操作类型
java -jar target/workspace-agent-0.2.0.jar --state-status D:\path\to\agent-state

# 创建带注释的项目配置模板；已存在时绝不覆盖
java -jar target/workspace-agent-0.2.0.jar --init-config D:\path\to\project

# 不扫描源码，只校验配置并显示最终生效值
java -jar target/workspace-agent-0.2.0.jar --validate-config D:\path\to\project

# 列出可用于禁用和豁免的全部稳定规则 ID
java -jar target/workspace-agent-0.2.0.jar --list-rules
```

使用 `--help` 查看完整命令，使用 `--version` 查看版本。未知选项、缺少参数或多余参数会输出帮助并返回退出码 `2`；所有参数都必须被明确消费，避免定时脚本的拼写错误被静默忽略。

`--serve` 启动后访问 `http://127.0.0.1:8787/`。服务在启动工作区向下四层发现项目，并在页面提供下拉选择；没有识别到构建清单或 Git 根时，将工作区本身作为单项目。服务只绑定本机回环地址，不接受其他电脑连接。`/api/projects` 返回启动时建立的项目白名单，所有分析接口只接受该白名单中的不透明 ID，不能用客户端路径越过启动工作区。页面调用 `/api/analysis?project=<id>`，后端只扫描一次并同时返回 `report` 与 `plan`，保证健康分、发现和优先行动来自同一时刻；兼容接口 `/api/report` 继续保留。页面展示行动排名、理由、预计恢复分和理论目标分。接口只执行静态只读分析，不执行被扫描项目；`/api/health` 可用于确认后端存活。按 `Ctrl+C` 停止服务。页面响应明确声明 UTF-8，因此不受 PowerShell 代码页影响。

Windows CLI 输出遵循 JVM 检测到的终端原生编码。若你在启动 Java 后又手工切换了代码页，请重新打开终端，或确保 `chcp` 与 Java 的 `stdout.encoding` 一致。

`--daily` 退出码约定：`0` 表示通过，`1` 表示运行故障，`2` 表示参数错误，`3` 表示质量门禁失败，`4` 表示构建失败，`5` 表示构建超时，`6` 表示同一状态目录已有任务运行。该约定便于任务计划程序和 CI 可靠判断结果。

每次 `--daily` 会对项目只执行一次分析，在状态目录中原子更新 `latest.html`、`latest.json` 与 `latest-plan.json`，追加 `history.tsv`，并在 `reports` 子目录保存带毫秒时间戳和唯一后缀的 Markdown。所有必要产物写入后，最后原子更新 `latest-run.json` 作为运行完成清单。健康报告、快照和行动计划因此来自同一份分析结果；快速连续执行也不会覆盖同时刻报告，历史报告不会被自动删除。

`latest-run.json` 包含完成时间、操作类型、组合通过状态、预期退出码、绝对与回归门禁细节、可选构建结果以及本轮所有产物路径。监控程序应同时检查文件可解析且 `completedAt` 晚于本次触发时间；单纯存在可能是上一次的完成清单。
历史 TSV 会校验精确表头、字段数量、数值范围、时间顺序和项目名一致性。同一文件不能混写多个项目；校验失败时不会更改原文件。

`--daily`、`--daily-verify` 和 `--portfolio-daily` 会对各自的状态目录持有跨进程锁。重叠启动的后来任务会立即返回 `6`，不会等待或覆盖前一次历史。锁文件本身会保留，是否正在运行由操作系统文件锁判定，不要仅凭文件存在与否判断。
使用 `--state-status` 可安全判断 `RUNNING` 或 `IDLE`；`IDLE` 时显示的元数据代表上一次持锁任务，不表示该 PID 仍在运行。

`--verify-build` 会执行项目代码，只应对可信项目显式调用；默认扫描和每日健康检查仍为静态只读分析。构建失败返回 `4`，超时返回 `5`，输出最多保留 64 KiB。

`--daily-verify` 同样只应对可信项目显式调用。它保留所有静态日报产物，原子更新 `latest-build.json` 与 `latest-build.log`，向 `build-history.jsonl` 追加不含大段输出的结构化摘要，并在 `build-logs` 中保留每次完整日志。静态门禁或构建任一失败都会返回非零退出码。

日检可选的“最大允许降幅”会将当前分数与上次快照比较。即使当前分数仍高于绝对最低线，超过允许降幅也会以退出码 `3` 阻断；首次运行只建立基线。日检还在独立的 `latest-risk-baseline.properties` 中保存未豁免高风险规则 ID；即使风险修复与新增互相抵消、总分不变，新增高风险仍会令门禁失败。该文件版本化并绑定规范化项目路径，不改变既有 `history.tsv` 格式。

JSON 输出包含 `schemaVersion`。消费者应按版本解析字段，不依赖字段排列顺序。`--plan-json` 还包含当前分、行动数、理论可恢复分、预计分以及按优先级排列的行动数组；预计值不代替修复后重新扫描。

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
- TODO/FIXME/HACK 证据最多保留 10 个相对路径与行号，不复制源码行内容。
- 依赖锁定检查覆盖 Maven/Gradle Wrapper、Node 锁文件、Python 锁文件、Cargo.lock 和 go.sum。
- CI 发现覆盖 GitHub Actions、GitLab CI、Azure Pipelines、CircleCI、Jenkins、Buildkite、Bitbucket Pipelines 和 Woodpecker；只检查路径与文件名，不解析或执行流水线。

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

可先运行 `--init-config`生成可直接解析的完整示例。该命令使用“仅当目标不存在时创建”语义；重复运行只会报告已存在，不会更改现有内容。
修改后可用 `--validate-config` 在不扫描源码的情况下预检。未知配置键、未知的禁用规则或豁免规则 ID 都会被视为错误，避免拼写错误静默失效。
使用 `--list-rules` 查看当前版本支持的全部 ID、类别和触发条件，不需要先制造对应风险来从报告中发现 ID。

忽略项只能是目录名，不能使用路径或 `..`。规则抑制使用报告中的稳定 `ruleId`，例如 `legal.license`；不要依赖中文文案。长期使用时优先采用 `waiver.<ruleId>=到期日|负责人|原因`：有效豁免保留发现但不扣分，到期后自动恢复扣分。评分权重限制为 0～100，且必须满足高风险 ≥ 中风险 ≥ 低风险。错误配置会令任务失败并返回退出码 `1`。

多项目巡检会先加载工作区根配置，再加载各项目配置；项目中的同名字段覆盖工作区默认值。单项目命令只读取项目自身配置。

`scan.maxFiles` 是返回结果上限。分析器会额外探测一个符合条件的文件：只有确认存在第 `maxFiles + 1` 个文件时才触发 `scan.file-limit`，恰好等于上限不会误报截断。
配置及内置的忽略目录会在遍历阶段直接剪枝，Agent 不会进入其中读取或枚举文件。

## 路线图

- 增加带审批策略的文件创建与补丁工具。
- 支持扫描结果缓存和增量分析。
- 增加任务历史、记忆和可恢复执行状态。
- 接入可选的本地模型或兼容 OpenAI 协议的模型。
- 增加更完善的自动化测试和打包发布流程。

产品背景见 [docs/VISION.md](docs/VISION.md)，设计边界见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)，威胁模型见 [docs/SECURITY.md](docs/SECURITY.md)。

贡献约定见 [CONTRIBUTING.md](CONTRIBUTING.md)，版本变化见 [CHANGELOG.md](CHANGELOG.md)。GitHub Actions 配置只授予源码只读权限，并使用 Maven Wrapper 执行同一套验证。

## 许可证

MIT License，详见 [LICENSE](LICENSE)。

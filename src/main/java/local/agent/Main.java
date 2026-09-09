package local.agent;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import local.agent.analysis.PortfolioAnalyzer;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.report.MarkdownReportWriter;
import local.agent.report.PortfolioReportWriter;
import local.agent.report.ReportStore;
import local.agent.report.JsonReportWriter;
import local.agent.history.HealthSnapshot;
import local.agent.history.SnapshotStore;
import local.agent.history.TrendReporter;
import local.agent.daily.DailyRunService;
import local.agent.report.ActionPlanWriter;
import local.agent.cli.CommandLine;
import local.agent.report.HtmlReportStore;
import local.agent.portfolio.PortfolioRunService;
import local.agent.verification.BuildVerifier;
import java.time.Duration;
import local.agent.report.BuildEvidenceStore;
import local.agent.state.RunAlreadyActiveException;
import local.agent.state.StateRunLock;
import local.agent.state.RunStatusInspector;
import local.agent.config.ConfigInitializer;
import local.agent.config.AnalyzerConfig;
import local.agent.report.ConfigSummaryWriter;
import local.agent.report.RuleCatalogWriter;
import local.agent.report.ActionPlanJsonWriter;
import local.agent.report.DailyRunManifestStore;
import local.agent.web.LocalWebServer;
import local.agent.rag.LocalRagService;
import local.agent.model.AiRagService;
import local.agent.model.ModelConfig;
import local.agent.model.OpenAiCompatibleClient;
import local.agent.model.ToolCallingAgentService;
import local.agent.model.AgentMemoryEntry;
import local.agent.model.AgentMemoryStore;
import local.agent.model.AgentCheckpointStore;

public final class Main {
    public static void main(String[] args) {
        configureUtf8Console();
        var argumentError = CommandLine.validate(args);
        if (argumentError.isPresent()) {
            System.err.println(argumentError.get());
            System.err.print(CommandLine.usage());
            System.exit(2);
        }
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.print(CommandLine.usage());
            return;
        }
        if (args.length == 1 && args[0].equals("--version")) {
            System.out.println("Project Sentinel " + CommandLine.VERSION);
            return;
        }
        if (args.length == 2 && args[0].equals("--tools-json")) {
            runToolDefinitions(Path.of(args[1]));
            return;
        }
        if (args.length == 4 && args[0].equals("--call")) {
            runFunctionCall(Path.of(args[1]), args[2], args[3]);
            return;
        }
        if (args.length == 3 && args[0].equals("--ask")) {
            runRag(Path.of(args[1]), args[2], false);
            return;
        }
        if (args.length == 3 && args[0].equals("--ask-json")) {
            runRag(Path.of(args[1]), args[2], true);
            return;
        }
        if (args.length == 3 && args[0].equals("--ask-ai")) {
            runAiRag(Path.of(args[1]), args[2]);
            return;
        }
        if (args.length == 3 && args[0].equals("--agent-ai")) {
            runToolCallingAgent(Path.of(args[1]), args[2]);
            return;
        }
        if (args.length == 4 && args[0].equals("--agent-ai-memory")) {
            runToolCallingAgentWithMemory(Path.of(args[1]), Path.of(args[2]), args[3]);
            return;
        }
        if (args.length >= 2 && args[0].equals("--check")) {
            runCheck(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--check-json")) {
            runCheckJson(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--plan")) {
            runPlan(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--plan-json")) {
            runPlanJson(Path.of(args[1]));
            return;
        }
        if (args.length >= 3 && args[0].equals("--report")) {
            runReport(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equals("--report-html")) {
            runHtmlReport(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--serve")) {
            int port = args.length >= 3 ? parsePort(args[2]) : 8787;
            Path stateDirectory = args.length >= 4 ? Path.of(args[3]) : null;
            runServer(Path.of(args[1]), port, stateDirectory);
            return;
        }
        if (args.length >= 2 && args[0].equals("--portfolio")) {
            runPortfolio(Path.of(args[1]));
            return;
        }
        if (args.length >= 3 && args[0].equals("--portfolio-daily")) {
            int minimum = args.length >= 4 ? parseMinimumScore(args[3]) : 70;
            runPortfolioDaily(Path.of(args[1]), Path.of(args[2]), minimum);
            return;
        }
        if (args.length >= 3 && args[0].equals("--snapshot")) {
            runSnapshot(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--trend")) {
            runTrend(Path.of(args[1]));
            return;
        }
        if (args.length >= 3 && args[0].equals("--daily")) {
            int minimum = args.length >= 4 ? parseMinimumScore(args[3]) : 70;
            int maximumDrop = args.length >= 5 ? parseScoreDrop(args[4]) : 100;
            runDaily(Path.of(args[1]), Path.of(args[2]), minimum, maximumDrop);
            return;
        }
        if (args.length >= 2 && args[0].equals("--verify-build")) {
            int seconds = args.length >= 3 ? parsePositiveSeconds(args[2]) : 120;
            runBuildVerification(Path.of(args[1]), seconds);
            return;
        }
        if (args.length >= 3 && args[0].equals("--daily-verify")) {
            int minimum = args.length >= 4 ? parseMinimumScore(args[3]) : 70;
            int seconds = args.length >= 5 ? parsePositiveSeconds(args[4]) : 120;
            int maximumDrop = args.length >= 6 ? parseScoreDrop(args[5]) : 100;
            runDailyVerify(Path.of(args[1]), Path.of(args[2]), minimum, seconds, maximumDrop);
            return;
        }
        if (args.length >= 2 && args[0].equals("--state-status")) {
            runStateStatus(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--init-config")) {
            runInitConfig(Path.of(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--validate-config")) {
            runValidateConfig(Path.of(args[1]));
            return;
        }
        if (args.length == 1 && args[0].equals("--list-rules")) {
            System.out.print(new RuleCatalogWriter().render());
            return;
        }
        Path workspace = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        if (!Files.isDirectory(workspace)) {
            System.err.println("工作区不存在或不是目录: " + workspace);
            System.exit(2);
        }
        var agent = new WorkspaceAgent(workspace);
        System.out.println("Project Sentinel " + CommandLine.VERSION);
        System.out.println("受限工作区: " + workspace.toAbsolutePath().normalize());
        System.out.println("输入“帮助”查看命令，输入“退出”结束。\n");
        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("agent> ");
                if (!scanner.hasNextLine()) break;
                String request = scanner.nextLine();
                if (request.equalsIgnoreCase("退出") || request.equalsIgnoreCase("exit")) break;
                System.out.println(agent.run(request));
            }
        }
    }

    private static void configureUtf8Console() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

    private static void runCheck(Path project) {
        try {
            System.out.print(new MarkdownReportWriter().render(new ProjectAnalyzer().analyze(project)));
        } catch (Exception e) {
            System.err.println("体检失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runCheckJson(Path project) {
        try {
            System.out.print(new JsonReportWriter().render(new ProjectAnalyzer().analyze(project)));
        } catch (Exception e) {
            System.err.println("JSON 体检失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runPlan(Path project) {
        try { System.out.print(new ActionPlanWriter().render(new ProjectAnalyzer().analyze(project))); }
        catch (Exception e) {
            System.err.println("行动规划失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runPlanJson(Path project) {
        try { System.out.print(new ActionPlanJsonWriter().render(new ProjectAnalyzer().analyze(project))); }
        catch (Exception e) {
            System.err.println("JSON 行动规划失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runReport(Path project, Path output) {
        try {
            var profile = new ProjectAnalyzer().analyze(project);
            Path saved = new ReportStore().save(profile, output);
            System.out.println("报告已保存: " + saved);
        } catch (Exception e) {
            System.err.println("报告生成失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runHtmlReport(Path project, Path outputFile) {
        try {
            Path saved = new HtmlReportStore().save(new ProjectAnalyzer().analyze(project), outputFile);
            System.out.println("HTML 报告已保存: " + saved);
        } catch (Exception e) {
            System.err.println("HTML 报告生成失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runPortfolio(Path workspace) {
        try {
            System.out.print(new PortfolioReportWriter().render(new PortfolioAnalyzer().analyze(workspace)));
        } catch (Exception e) {
            System.err.println("巡检失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runPortfolioDaily(Path workspace, Path stateDirectory, int minimumScore) {
        try (var ignored = StateRunLock.acquire(stateDirectory, "portfolio-daily")) {
            var result = new PortfolioRunService().run(workspace, stateDirectory, minimumScore);
            System.out.println("项目数: " + result.projectCount());
            System.out.println("最低健康分: " + result.lowestScore());
            System.out.println("组合 HTML: " + result.html());
            System.out.println("组合 JSON: " + result.json());
            System.out.println("组合门禁: " + (result.passed() ? "通过" : "未通过"));
            if (!result.passed()) System.exit(3);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage()); System.exit(2);
        } catch (RunAlreadyActiveException e) {
            System.err.println(e.getMessage()); System.exit(6);
        } catch (Exception e) {
            System.err.println("组合巡检失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void runSnapshot(Path project, Path historyFile) {
        try {
            var snapshot = HealthSnapshot.from(new ProjectAnalyzer().analyze(project));
            new SnapshotStore().append(historyFile, snapshot);
            System.out.println("健康快照已追加: " + historyFile.toAbsolutePath().normalize());
        } catch (Exception e) {
            System.err.println("快照失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runTrend(Path historyFile) {
        try { System.out.print(new TrendReporter().render(new SnapshotStore().read(historyFile))); }
        catch (Exception e) {
            System.err.println("趋势分析失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static int parseMinimumScore(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) {
            System.err.println("最低健康分必须是整数: " + value);
            System.exit(2);
            return -1;
        }
    }

    private static int parsePositiveSeconds(String value) {
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 1 || seconds > 1800) throw new NumberFormatException();
            return seconds;
        } catch (NumberFormatException e) {
            System.err.println("超时秒数必须是 1 到 1800 的整数: " + value); System.exit(2); return -1;
        }
    }

    private static int parseScoreDrop(String value) {
        try {
            int score = Integer.parseInt(value);
            if (score < 0 || score > 100) throw new NumberFormatException();
            return score;
        } catch (NumberFormatException e) {
            System.err.println("最大允许降幅必须是 0 到 100 的整数: " + value); System.exit(2); return -1;
        }
    }

    private static void runBuildVerification(Path project, int timeoutSeconds) {
        try {
            var result = new BuildVerifier().verify(project, Duration.ofSeconds(timeoutSeconds));
            System.out.println("状态: " + result.status());
            System.out.println("命令: " + String.join(" ", result.command()));
            System.out.println("耗时: " + result.duration().toMillis() + " ms");
            System.out.print(result.output());
            if (!result.passed()) System.exit(result.status() == local.agent.verification.BuildVerification.Status.TIMED_OUT ? 5 : 4);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage()); System.exit(2);
        } catch (Exception e) {
            System.err.println("构建验证失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void runDailyVerify(Path project, Path stateDirectory, int minimumScore, int timeoutSeconds, int maximumDrop) {
        try (var ignored = StateRunLock.acquire(stateDirectory, "daily-verify")) {
            var daily = new DailyRunService().run(project, stateDirectory, minimumScore, maximumDrop);
            var build = new BuildVerifier().verify(project, Duration.ofSeconds(timeoutSeconds));
            var evidence = new BuildEvidenceStore().save(stateDirectory, build);
            Path manifest = new DailyRunManifestStore().save(stateDirectory, "daily-verify", daily, build, evidence);
            System.out.print(daily.trend());
            printDailyGate(daily);
            System.out.println("构建状态: " + build.status() + "，耗时: " + build.duration().toMillis() + " ms");
            System.out.println("构建 JSON: " + evidence.latestJson());
            System.out.println("构建日志: " + evidence.latestLog());
            System.out.println("构建历史: " + evidence.history());
            System.out.println("归档日志: " + evidence.archivedLog());
            System.out.println("最新行动计划: " + daily.latestPlanJson());
            System.out.println("运行完成清单: " + manifest);
            if (!build.passed()) System.exit(build.status() == local.agent.verification.BuildVerification.Status.TIMED_OUT ? 5 : 4);
            if (!daily.passed()) System.exit(3);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage()); System.exit(2);
        } catch (RunAlreadyActiveException e) {
            System.err.println(e.getMessage()); System.exit(6);
        } catch (Exception e) {
            System.err.println("每日构建验证失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void runDaily(Path project, Path stateDirectory, int minimumScore, int maximumDrop) {
        try (var ignored = StateRunLock.acquire(stateDirectory, "daily")) {
            var result = new DailyRunService().run(project, stateDirectory, minimumScore, maximumDrop);
            Path manifest = new DailyRunManifestStore().save(stateDirectory, "daily", result, null, null);
            System.out.print(result.trend());
            System.out.println("报告: " + result.report());
            System.out.println("最新 HTML: " + result.latestHtml());
            System.out.println("最新 JSON: " + result.latestJson());
            System.out.println("最新行动计划: " + result.latestPlanJson());
            System.out.println("运行完成清单: " + manifest);
            System.out.println("质量门禁: " + (result.passed() ? "通过" : "未通过")
                    + "（当前 " + result.score() + "，最低 " + result.minimumScore() + "）");
            printRegressionGate(result);
            if (!result.passed()) System.exit(3);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            System.exit(2);
        } catch (RunAlreadyActiveException e) {
            System.err.println(e.getMessage()); System.exit(6);
        } catch (Exception e) {
            System.err.println("每日运行失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printDailyGate(local.agent.daily.DailyRunResult result) {
        System.out.println("静态健康分: " + result.score() + "，分数门禁: " + (result.scorePassed() ? "通过" : "未通过"));
        printRegressionGate(result);
    }

    private static void runStateStatus(Path stateDirectory) {
        try {
            var status = new RunStatusInspector().inspect(stateDirectory);
            System.out.println("状态: " + (status.active() ? "RUNNING" : "IDLE"));
            if (status.metadata() != null) {
                System.out.println("进程: " + (status.metadata().processId() < 0 ? "unknown" : status.metadata().processId()));
                System.out.println("开始: " + (status.metadata().startedAt() == null ? "unknown" : status.metadata().startedAt()));
                System.out.println("操作: " + status.metadata().operation());
            }
            var checkpoint = AgentCheckpointStore.inspect(stateDirectory);
            System.out.println("Agent 检查点: " + (!checkpoint.present() ? "NONE" : checkpoint.completed() ? "COMPLETED" : "ACTIVE"));
            if (checkpoint.present() && !checkpoint.completed()) {
                System.out.println("检查点工作区: " + checkpoint.workspace());
                System.out.println("检查点任务: " + checkpoint.task());
                System.out.println("已完成模型轮次: " + checkpoint.completedRounds());
                System.out.println("已完成工具调用: " + checkpoint.toolCalls());
            }
        } catch (Exception e) {
            System.err.println("状态查询失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void runInitConfig(Path project) {
        try {
            var result = new ConfigInitializer().initialize(project);
            System.out.println(result.created() ? "配置已创建: " + result.path() : "配置已存在，未修改: " + result.path());
        } catch (Exception e) {
            System.err.println("配置初始化失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void runValidateConfig(Path project) {
        try {
            Path root = project.toRealPath();
            if (!Files.isDirectory(root)) throw new java.io.IOException("项目不是目录: " + root);
            var config = AnalyzerConfig.load(root);
            boolean configured = Files.isRegularFile(root.resolve(AnalyzerConfig.FILE_NAME));
            System.out.print(new ConfigSummaryWriter().render(root, config, configured));
        } catch (Exception e) {
            System.err.println("配置无效: " + e.getMessage()); System.exit(1);
        }
    }

    private static void printRegressionGate(local.agent.daily.DailyRunResult result) {
        if (result.previousScore() == null) {
            System.out.println("分数回归门禁: 无上次快照，本次作为基线");
        } else {
            System.out.println("分数回归门禁: " + (result.scoreRegressionPassed() ? "通过" : "未通过")
                    + "（降幅 " + result.scoreDrop() + "，最大允许 " + result.maximumScoreDrop() + "）");
        }
        if (!result.hadRiskBaseline()) System.out.println("风险回归门禁: 无上次风险基线，本次作为基线");
        else System.out.println("风险回归门禁: " + (result.riskRegressionPassed() ? "通过" : "未通过")
                + "（新增未豁免高风险规则 " + result.newHighRiskRuleIds() + "）");
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("端口必须是 1 到 65535 之间的整数");
        }
    }

    private static void runServer(Path project, int port, Path stateDirectory) {
        try (var server = stateDirectory == null ? new LocalWebServer(project, port)
                : new LocalWebServer(project, port, stateDirectory)) {
            server.start();
            System.out.println("Project Sentinel 本地服务已启动: " + server.url());
            System.out.println("按 Ctrl+C 停止。服务仅监听本机回环地址。");
            if (stateDirectory != null) System.out.println("Web Agent 持久状态目录: " + stateDirectory.toAbsolutePath().normalize());
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("本地服务启动失败: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runToolDefinitions(Path workspace) {
        try { System.out.print(new WorkspaceAgent(workspace).toolDefinitionsJson()); }
        catch (Exception e) { System.err.println("工具目录生成失败: " + e.getMessage()); System.exit(1); }
    }

    private static void runFunctionCall(Path workspace, String name, String input) {
        try {
            var result = new WorkspaceAgent(workspace).callFunction(null, name, input);
            System.out.print(result.toJson());
            if (!result.success()) System.exit(3);
        } catch (Exception e) { System.err.println("函数调用失败: " + e.getMessage()); System.exit(1); }
    }

    private static void runRag(Path workspace, String question, boolean json) {
        try {
            var answer = new LocalRagService(new WorkspaceGuard(workspace)).ask(question);
            System.out.print(json ? answer.toJson() : answer.renderText());
        } catch (IllegalArgumentException e) { System.err.println("RAG 参数错误: " + e.getMessage()); System.exit(2); }
        catch (Exception e) { System.err.println("RAG 检索失败: " + e.getMessage()); System.exit(1); }
    }

    private static void runAiRag(Path workspace, String question) {
        try {
            var rag = new LocalRagService(new WorkspaceGuard(workspace));
            var model = new OpenAiCompatibleClient(ModelConfig.fromEnvironment());
            System.out.print(new AiRagService(rag, model).ask(question).renderText());
        } catch (IllegalArgumentException e) { System.err.println("模型配置错误: " + e.getMessage()); System.exit(2); }
        catch (Exception e) { System.err.println("模型增强 RAG 失败: " + e.getMessage()); System.exit(1); }
    }

    private static void runToolCallingAgent(Path workspace, String task) {
        try {
            var agent = new WorkspaceAgent(workspace);
            var model = new OpenAiCompatibleClient(ModelConfig.fromEnvironment());
            System.out.print(new ToolCallingAgentService(agent, model).run(task).renderText());
        } catch (IllegalArgumentException e) { System.err.println("Agent 参数或模型配置错误: " + e.getMessage()); System.exit(2); }
        catch (Exception e) { System.err.println("Function Calling Agent 失败: " + e.getMessage()); System.exit(1); }
    }

    private static void runToolCallingAgentWithMemory(Path workspace, Path stateDirectory, String task) {
        try {
            var config = ModelConfig.fromEnvironment();
            try (var ignored = StateRunLock.acquire(stateDirectory, "agent-ai-memory")) {
                var memory = new AgentMemoryStore(workspace, stateDirectory);
                var agent = new WorkspaceAgent(workspace);
                var model = new OpenAiCompatibleClient(config);
                var checkpoints = new AgentCheckpointStore(workspace, stateDirectory);
                var result = new ToolCallingAgentService(agent, model).runResumable(task, memory.readRecent(5), checkpoints);
                memory.append(new AgentMemoryEntry(java.time.Instant.now(), task, result.answer(), result.modelRounds(), result.toolCalls()));
                System.out.print(result.renderText());
                System.out.println("记忆文件：" + memory.file());
            }
        } catch (RunAlreadyActiveException e) { System.err.println(e.getMessage()); System.exit(6); }
        catch (IllegalArgumentException e) { System.err.println("Agent 参数或模型配置错误: " + e.getMessage()); System.exit(2); }
        catch (Exception e) { System.err.println("带记忆 Agent 失败: " + e.getMessage()); System.exit(1); }
    }
}

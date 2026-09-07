package local.agent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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

public final class Main {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
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
            System.out.println("Workspace Agent " + CommandLine.VERSION);
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
        if (args.length >= 3 && args[0].equals("--report")) {
            runReport(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equals("--report-html")) {
            runHtmlReport(Path.of(args[1]), Path.of(args[2]));
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
        Path workspace = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        if (!Files.isDirectory(workspace)) {
            System.err.println("工作区不存在或不是目录: " + workspace);
            System.exit(2);
        }
        var agent = new WorkspaceAgent(workspace);
        System.out.println("Workspace Agent " + CommandLine.VERSION);
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
            System.out.print(daily.trend());
            printDailyGate(daily);
            System.out.println("构建状态: " + build.status() + "，耗时: " + build.duration().toMillis() + " ms");
            System.out.println("构建 JSON: " + evidence.latestJson());
            System.out.println("构建日志: " + evidence.latestLog());
            System.out.println("构建历史: " + evidence.history());
            System.out.println("归档日志: " + evidence.archivedLog());
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
            System.out.print(result.trend());
            System.out.println("报告: " + result.report());
            System.out.println("最新 HTML: " + result.latestHtml());
            System.out.println("最新 JSON: " + result.latestJson());
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
        } catch (Exception e) {
            System.err.println("状态查询失败: " + e.getMessage()); System.exit(1);
        }
    }

    private static void printRegressionGate(local.agent.daily.DailyRunResult result) {
        if (result.previousScore() == null) {
            System.out.println("回归门禁: 无上次快照，本次作为基线");
        } else {
            System.out.println("回归门禁: " + (result.regressionPassed() ? "通过" : "未通过")
                    + "（降幅 " + result.scoreDrop() + "，最大允许 " + result.maximumScoreDrop() + "）");
        }
    }
}

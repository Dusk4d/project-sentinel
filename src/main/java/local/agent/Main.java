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
            runDaily(Path.of(args[1]), Path.of(args[2]), minimum);
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

    private static void runDaily(Path project, Path stateDirectory, int minimumScore) {
        try {
            var result = new DailyRunService().run(project, stateDirectory, minimumScore);
            System.out.print(result.trend());
            System.out.println("报告: " + result.report());
            System.out.println("质量门禁: " + (result.passed() ? "通过" : "未通过")
                    + "（当前 " + result.score() + "，最低 " + result.minimumScore() + "）");
            if (!result.passed()) System.exit(3);
        } catch (IllegalArgumentException e) {
            System.err.println("参数错误: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("每日运行失败: " + e.getMessage());
            System.exit(1);
        }
    }
}

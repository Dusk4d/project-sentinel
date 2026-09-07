package local.agent.daily;

import local.agent.analysis.ProjectAnalyzer;
import local.agent.history.HealthSnapshot;
import local.agent.history.SnapshotStore;
import local.agent.history.TrendReporter;
import local.agent.report.ReportStore;
import local.agent.report.HtmlReportStore;
import local.agent.report.JsonReportStore;
import local.agent.report.ActionPlanJsonStore;

import java.io.IOException;
import java.nio.file.Path;

public final class DailyRunService {
    public DailyRunResult run(Path project, Path stateDirectory, int minimumScore) throws IOException {
        return run(project, stateDirectory, minimumScore, 100);
    }

    public DailyRunResult run(Path project, Path stateDirectory, int minimumScore, int maximumScoreDrop) throws IOException {
        if (minimumScore < 0 || minimumScore > 100) throw new IllegalArgumentException("最低健康分必须在 0 到 100 之间");
        if (maximumScoreDrop < 0 || maximumScoreDrop > 100)
            throw new IllegalArgumentException("最大允许降幅必须在 0 到 100 之间");
        Path projectRoot = project.toAbsolutePath().normalize();
        Path stateRoot = stateDirectory.toAbsolutePath().normalize();
        var profile = new ProjectAnalyzer().analyze(projectRoot);
        Path reports = stateRoot.resolve("reports");
        Path history = stateRoot.resolve("history.tsv");
        Path report = new ReportStore().save(profile, reports);
        Path latestHtml = new HtmlReportStore().save(profile, stateRoot.resolve("latest.html"));
        Path latestJson = new JsonReportStore().save(profile, stateRoot.resolve("latest.json"));
        Path latestPlanJson = new ActionPlanJsonStore().save(profile, stateRoot.resolve("latest-plan.json"));
        var snapshots = new SnapshotStore();
        var before = snapshots.read(history);
        Integer previousScore = before.isEmpty() ? null : before.get(before.size() - 1).score();
        snapshots.append(history, HealthSnapshot.from(profile));
        String trend = new TrendReporter().render(snapshots.read(history));
        return new DailyRunResult(profile.healthScore(), minimumScore, previousScore, maximumScoreDrop,
                report, latestHtml, latestJson, latestPlanJson, history, trend);
    }
}

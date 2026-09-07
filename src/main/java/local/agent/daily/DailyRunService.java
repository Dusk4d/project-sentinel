package local.agent.daily;

import local.agent.analysis.ProjectAnalyzer;
import local.agent.history.HealthSnapshot;
import local.agent.history.SnapshotStore;
import local.agent.history.TrendReporter;
import local.agent.report.ReportStore;

import java.io.IOException;
import java.nio.file.Path;

public final class DailyRunService {
    public DailyRunResult run(Path project, Path stateDirectory, int minimumScore) throws IOException {
        if (minimumScore < 0 || minimumScore > 100) throw new IllegalArgumentException("最低健康分必须在 0 到 100 之间");
        Path projectRoot = project.toAbsolutePath().normalize();
        Path stateRoot = stateDirectory.toAbsolutePath().normalize();
        var profile = new ProjectAnalyzer().analyze(projectRoot);
        Path reports = stateRoot.resolve("reports");
        Path history = stateRoot.resolve("history.tsv");
        Path report = new ReportStore().save(profile, reports);
        var snapshots = new SnapshotStore();
        snapshots.append(history, HealthSnapshot.from(profile));
        String trend = new TrendReporter().render(snapshots.read(history));
        return new DailyRunResult(profile.healthScore(), minimumScore, report, history, trend);
    }
}

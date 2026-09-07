package local.agent.report;

import local.agent.daily.DailyRunResult;
import local.agent.verification.BuildVerification;

import java.time.Instant;

public final class DailyRunManifestWriter {
    public String render(String operation, Instant completedAt, DailyRunResult daily,
                         BuildVerification build, BuildEvidencePaths evidence) {
        int expectedExitCode = expectedExitCode(daily, build);
        var out = new StringBuilder("{\n")
                .append("  \"schemaVersion\": 1,\n")
                .append("  \"operation\": ").append(JsonReportWriter.quote(operation)).append(",\n")
                .append("  \"completedAt\": ").append(JsonReportWriter.quote(completedAt.toString())).append(",\n")
                .append("  \"passed\": ").append(expectedExitCode == 0).append(",\n")
                .append("  \"expectedExitCode\": ").append(expectedExitCode).append(",\n")
                .append("  \"staticGate\": {\n")
                .append("    \"score\": ").append(daily.score()).append(",\n")
                .append("    \"minimumScore\": ").append(daily.minimumScore()).append(",\n")
                .append("    \"previousScore\": ").append(daily.previousScore() == null ? "null" : daily.previousScore()).append(",\n")
                .append("    \"maximumScoreDrop\": ").append(daily.maximumScoreDrop()).append(",\n")
                .append("    \"scoreDrop\": ").append(daily.scoreDrop()).append(",\n")
                .append("    \"scorePassed\": ").append(daily.scorePassed()).append(",\n")
                .append("    \"regressionPassed\": ").append(daily.regressionPassed()).append(",\n")
                .append("    \"passed\": ").append(daily.passed()).append("\n  },\n")
                .append("  \"build\": ");
        if (build == null) {
            out.append("null,\n");
        } else {
            out.append("{\"status\": ").append(JsonReportWriter.quote(build.status().name()))
                    .append(", \"passed\": ").append(build.passed())
                    .append(", \"exitCode\": ").append(build.exitCode())
                    .append(", \"durationMillis\": ").append(build.duration().toMillis()).append("},\n");
        }
        out.append("  \"artifacts\": {\n")
                .append("    \"report\": ").append(path(daily.report())).append(",\n")
                .append("    \"latestHtml\": ").append(path(daily.latestHtml())).append(",\n")
                .append("    \"latestJson\": ").append(path(daily.latestJson())).append(",\n")
                .append("    \"latestPlanJson\": ").append(path(daily.latestPlanJson())).append(",\n")
                .append("    \"history\": ").append(path(daily.history())).append(",\n")
                .append("    \"latestBuildJson\": ").append(evidence == null ? "null" : path(evidence.latestJson())).append(",\n")
                .append("    \"latestBuildLog\": ").append(evidence == null ? "null" : path(evidence.latestLog())).append(",\n")
                .append("    \"buildHistory\": ").append(evidence == null ? "null" : path(evidence.history())).append(",\n")
                .append("    \"archivedBuildLog\": ").append(evidence == null ? "null" : path(evidence.archivedLog())).append("\n")
                .append("  }\n}\n");
        return out.toString();
    }

    private int expectedExitCode(DailyRunResult daily, BuildVerification build) {
        if (build != null && !build.passed())
            return build.status() == BuildVerification.Status.TIMED_OUT ? 5 : 4;
        return daily.passed() ? 0 : 3;
    }

    private String path(java.nio.file.Path value) { return JsonReportWriter.quote(value.toAbsolutePath().normalize().toString()); }
}

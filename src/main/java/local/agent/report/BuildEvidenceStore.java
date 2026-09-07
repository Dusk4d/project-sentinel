package local.agent.report;

import local.agent.verification.BuildVerification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class BuildEvidenceStore {
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS'Z'").withZone(ZoneOffset.UTC);

    public BuildEvidencePaths save(Path stateDirectory, BuildVerification result) throws IOException {
        return save(stateDirectory, result, Instant.now());
    }

    public BuildEvidencePaths save(Path stateDirectory, BuildVerification result, Instant observedAt) throws IOException {
        Path state = stateDirectory.toAbsolutePath().normalize();
        Path logDirectory = state.resolve("build-logs");
        Files.createDirectories(logDirectory);
        Path archivedLog = uniqueLog(logDirectory, FILE_TIME.format(observedAt));
        var atomic = new AtomicTextStore();
        atomic.write(archivedLog, result.output());

        Path latestJson = atomic.write(state.resolve("latest-build.json"), new BuildVerificationJsonWriter().render(result));
        Path latestLog = atomic.write(state.resolve("latest-build.log"), result.output());
        Path history = state.resolve("build-history.jsonl");
        String existing = Files.exists(history) ? Files.readString(history, StandardCharsets.UTF_8) : "";
        String relativeLog = state.relativize(archivedLog).toString().replace('\\', '/');
        String row = historyRow(result, observedAt, relativeLog) + "\n";
        atomic.write(history, existing + row);
        return new BuildEvidencePaths(latestJson, latestLog, history.toAbsolutePath().normalize(), archivedLog);
    }

    private Path uniqueLog(Path directory, String stem) {
        Path candidate = directory.resolve(stem + ".log");
        for (int suffix = 2; Files.exists(candidate); suffix++) candidate = directory.resolve(stem + "-" + suffix + ".log");
        return candidate;
    }

    private String historyRow(BuildVerification result, Instant observedAt, String relativeLog) {
        var out = new StringBuilder("{\"schemaVersion\":1,\"observedAt\":")
                .append(JsonReportWriter.quote(observedAt.toString()))
                .append(",\"status\":").append(JsonReportWriter.quote(result.status().name()))
                .append(",\"passed\":").append(result.passed())
                .append(",\"exitCode\":").append(result.exitCode())
                .append(",\"durationMillis\":").append(result.duration().toMillis())
                .append(",\"command\":[");
        for (int i = 0; i < result.command().size(); i++) {
            if (i > 0) out.append(',');
            out.append(JsonReportWriter.quote(result.command().get(i)));
        }
        return out.append("],\"log\":").append(JsonReportWriter.quote(relativeLog)).append('}').toString();
    }
}

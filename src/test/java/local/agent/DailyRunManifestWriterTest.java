package local.agent;

import local.agent.daily.DailyRunResult;
import local.agent.report.BuildEvidencePaths;
import local.agent.report.DailyRunManifestWriter;
import local.agent.verification.BuildVerification;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class DailyRunManifestWriterTest {
    private final DailyRunResult daily = new DailyRunResult(82, 80, 90, 5,
            Path.of("report.md"), Path.of("latest.html"), Path.of("latest.json"),
            Path.of("latest-plan.json"), Path.of("history.tsv"), "trend", true,
            Set.of("security.sensitive-file"), Path.of("risk.properties"));

    @Test void rendersStaticRegressionFailureAsCompletionContract() {
        String json = new DailyRunManifestWriter().render("daily", Instant.parse("2026-09-08T02:00:00Z"),
                daily, null, null);
        assertTrue(json.contains("\"operation\": \"daily\""));
        assertTrue(json.contains("\"passed\": false"));
        assertTrue(json.contains("\"expectedExitCode\": 3"));
        assertTrue(json.contains("\"scoreDrop\": 8"));
        assertTrue(json.contains("\"newHighRiskRuleIds\": [\"security.sensitive-file\"]"));
        assertTrue(json.contains("\"riskBaseline\": "));
        assertTrue(json.contains("\"build\": null"));
        assertTrue(json.contains("\"latestBuildJson\": null"));
    }

    @Test void buildTimeoutTakesExitCodePrecedenceAndLinksEvidence() {
        var build = new BuildVerification(BuildVerification.Status.TIMED_OUT, List.of("tool", "test"), -1,
                Duration.ofSeconds(10), "timeout");
        var evidence = new BuildEvidencePaths(Path.of("build.json"), Path.of("build.log"),
                Path.of("history.jsonl"), Path.of("archive.log"));
        String json = new DailyRunManifestWriter().render("daily-verify", Instant.EPOCH, daily, build, evidence);
        assertTrue(json.contains("\"expectedExitCode\": 5"));
        assertTrue(json.contains("\"status\": \"TIMED_OUT\""));
        assertTrue(json.contains("build.json"));
        assertTrue(json.contains("archive.log"));
    }
}

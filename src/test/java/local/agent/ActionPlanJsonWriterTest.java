package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.report.ActionPlanJsonWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ActionPlanJsonWriterTest {
    @Test void rendersVersionedOrderedPlanAndEscapesEvidence() {
        var profile = new ProjectProfile(Path.of("demo"), "demo", "unknown", 0, 0, 0, 0,
                false, false, false, List.of(
                new Finding("docs.demo", Severity.LOW, "docs", "line1\nline2", "x\\y", "write \"docs\""),
                new Finding("build.demo", Severity.HIGH, "build", "missing", "none", "add build")));
        String json = new ActionPlanJsonWriter().render(profile);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"actionCount\": 2"));
        assertTrue(json.contains("\"currentHealthScore\": 70"));
        assertTrue(json.contains("\"potentialScoreRecovery\": 30"));
        assertTrue(json.contains("\"projectedHealthScore\": 100"));
        assertTrue(json.indexOf("build.demo") < json.indexOf("docs.demo"));
        assertTrue(json.contains("write \\\"docs\\\""));
        assertFalse(json.contains("line1\nline2"));
    }
}

package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.report.JsonReportWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.time.LocalDate;
import local.agent.analysis.RuleWaiver;

import static org.junit.jupiter.api.Assertions.*;

final class JsonReportWriterTest {
    @Test void rendersStableSchemaAndEscapesUntrustedText() {
        var profile = new ProjectProfile(Path.of("C:\\work\\demo"), "a\"b", "Java\nMaven", 4, 2, 1, 0,
                true, true, true, List.of(new Finding("security.demo", Severity.HIGH, "安全", "line1\nline2", "x\\y", "fix\tit")));
        String json = new JsonReportWriter().render(profile);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"scoreWeights\": {\"high\": 25, \"medium\": 12, \"low\": 5}"));
        assertTrue(json.contains("\"ruleId\": \"security.demo\""));
        assertTrue(json.contains("\"project\": \"a\\\"b\""));
        assertTrue(json.contains("Java\\nMaven"));
        assertTrue(json.contains("x\\\\y"));
        assertFalse(json.contains("line1\nline2"));
    }

    @Test void exposesAuditableWaiverMetadata() {
        var waiver = new RuleWaiver("legal.license", LocalDate.of(2099, 12, 31), "alice", "legal review");
        var finding = new Finding("legal.license", Severity.LOW, "合规", "missing", "none", "add", waiver);
        var profile = new ProjectProfile(Path.of("demo"), "demo", "unknown", 0, 0, 0, 0,
                false, false, false, List.of(finding));
        String json = new JsonReportWriter().render(profile);
        assertTrue(json.contains("\"waived\": true"));
        assertTrue(json.contains("\"expiresOn\": \"2099-12-31\""));
        assertTrue(json.contains("\"owner\": \"alice\""));
        assertEquals(100, profile.healthScore());
    }
}

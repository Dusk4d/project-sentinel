package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.report.JsonReportWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class JsonReportWriterTest {
    @Test void rendersStableSchemaAndEscapesUntrustedText() {
        var profile = new ProjectProfile(Path.of("C:\\work\\demo"), "a\"b", "Java\nMaven", 4, 2, 1, 0,
                true, true, true, List.of(new Finding(Severity.HIGH, "安全", "line1\nline2", "x\\y", "fix\tit")));
        String json = new JsonReportWriter().render(profile);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"project\": \"a\\\"b\""));
        assertTrue(json.contains("Java\\nMaven"));
        assertTrue(json.contains("x\\\\y"));
        assertFalse(json.contains("line1\nline2"));
    }
}

package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.report.AnalysisBundleJsonWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AnalysisBundleJsonWriterTest {
    @Test void embedsReportAndPlanFromOneProfile() {
        var profile = new ProjectProfile(Path.of("demo"), "demo", "unknown", 1, 1, 0, 0,
                false, false, false, List.of(new Finding("tests.demo", Severity.HIGH,
                "测试", "缺少测试", "0 tests", "增加测试")));
        String json = new AnalysisBundleJsonWriter().render(profile);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"generatedAt\":"));
        assertTrue(json.contains("\"report\": {"));
        assertTrue(json.contains("\"plan\": {"));
        assertTrue(json.contains("\"actionCount\": 1"));
        assertTrue(json.contains("\"action\": \"增加测试\""));
    }
}

package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.planning.ActionPlanner;
import local.agent.report.ActionPlanWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ActionPlannerTest {
    @Test void ordersRisksBySeverityAndExcludesInformation() {
        var profile = new ProjectProfile(Path.of("demo"), "demo", "unknown", 0, 0, 0, 0, false, false, false,
                List.of(
                        new Finding(Severity.LOW, "docs", "low", "e1", "write docs"),
                        new Finding(Severity.INFO, "info", "ok", "e2", "observe"),
                        new Finding(Severity.HIGH, "build", "high", "e3", "add build"),
                        new Finding(Severity.MEDIUM, "tests", "medium", "e4", "add tests")));
        var plan = new ActionPlanner().plan(profile);
        assertEquals(3, plan.size());
        assertEquals(Severity.HIGH, plan.get(0).severity());
        assertEquals(25, plan.get(0).potentialScoreGain());
        assertEquals(Severity.LOW, plan.get(2).severity());
        String rendered = new ActionPlanWriter().render(profile);
        assertTrue(rendered.indexOf("add build") < rendered.indexOf("add tests"));
        assertFalse(rendered.contains("observe"));
    }
}

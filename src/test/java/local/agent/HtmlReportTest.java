package local.agent;

import local.agent.analysis.Finding;
import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.report.HtmlReportStore;
import local.agent.report.HtmlReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.LocalDate;
import local.agent.analysis.RuleWaiver;

import static org.junit.jupiter.api.Assertions.*;

final class HtmlReportTest {
    @TempDir Path temp;

    @Test void escapesUntrustedContentAndWritesStandaloneDocument() throws Exception {
        var profile = new ProjectProfile(temp, "<script>alert(1)</script>", "Java & Maven", 1, 1, 0, 0,
                true, true, true, List.of(new Finding("security.demo", Severity.HIGH, "安全", "bad <tag>",
                "x & y", "use \"safe\" value")));
        String html = new HtmlReportWriter().render(profile);
        assertTrue(html.startsWith("<!doctype html>"));
        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("Java &amp; Maven"));
        Path output = new HtmlReportStore().save(profile, temp.resolve("dashboard.html"));
        assertTrue(Files.isRegularFile(output));
        assertEquals(html, Files.readString(output));
    }

    @Test void displaysEscapedWaiverDetails() {
        var waiver = new RuleWaiver("tests.ratio", LocalDate.of(2099, 1, 2), "a<b", "migration & review");
        var finding = new Finding("tests.ratio", Severity.MEDIUM, "测试", "few tests", "1/10", "add tests", waiver);
        var profile = new ProjectProfile(temp, "demo", "Java", 1, 1, 0, 0, true, true, true, List.of(finding));
        String html = new HtmlReportWriter().render(profile);
        assertTrue(html.contains("已豁免至 2099-01-02"));
        assertTrue(html.contains("a&lt;b"));
        assertTrue(html.contains("migration &amp; review"));
        assertEquals(100, profile.healthScore());
    }
}

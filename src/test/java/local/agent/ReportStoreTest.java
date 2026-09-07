package local.agent;

import local.agent.analysis.ProjectProfile;
import local.agent.report.ReportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ReportStoreTest {
    @TempDir Path temp;

    @Test void preservesTwoReportsCreatedAtExactlySameTime() throws Exception {
        var profile = new ProjectProfile(temp, "demo/name", "unknown", 0, 0, 0, 0,
                true, true, true, List.of());
        var store = new ReportStore();
        var sameTime = LocalDateTime.of(2026, 9, 8, 2, 30, 45, 123_000_000);

        Path first = store.save(profile, temp.resolve("reports"), sameTime);
        Path second = store.save(profile, temp.resolve("reports"), sameTime);

        assertNotEquals(first, second);
        assertTrue(Files.isRegularFile(first));
        assertTrue(Files.isRegularFile(second));
        assertTrue(first.getFileName().toString().startsWith("20260908-023045-123-demo_name-"));
        assertTrue(Files.readString(first).startsWith("# 项目健康报告：demo/name"));
        assertTrue(Files.readString(second).startsWith("# 项目健康报告：demo/name"));
        try (var files = Files.list(temp.resolve("reports"))) {
            assertEquals(2, files.count());
        }
    }
}

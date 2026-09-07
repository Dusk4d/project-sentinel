package local.agent;

import local.agent.history.HealthSnapshot;
import local.agent.history.SnapshotStore;
import local.agent.history.TrendReporter;
import java.nio.file.Files;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class SnapshotStoreTest {
    @TempDir Path dir;
    @Test void persistsAndReportsTrend() throws Exception {
        var file = dir.resolve("history.tsv");
        var store = new SnapshotStore();
        store.append(file, new HealthSnapshot(Instant.parse("2026-01-01T00:00:00Z"), "demo", 70, 10, 5, 1, 8));
        store.append(file, new HealthSnapshot(Instant.parse("2026-01-02T00:00:00Z"), "demo", 82, 12, 6, 2, 5));
        var history = store.read(file);
        assertEquals(2, history.size());
        String trend = new TrendReporter().render(history);
        assertTrue(trend.contains("+12"));
        assertTrue(trend.contains("整体改善"));
    }
}

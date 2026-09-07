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

    @Test void rejectsInvalidHeaderAndMalformedValues() throws Exception {
        var store = new SnapshotStore();
        var invalidHeader = dir.resolve("bad-header.tsv");
        Files.writeString(invalidHeader, "wrong\theader\n");
        assertTrue(assertThrows(java.io.IOException.class, () -> store.read(invalidHeader)).getMessage().contains("表头"));

        var invalidScore = dir.resolve("bad-score.tsv");
        Files.writeString(invalidScore, "timestamp\tproject\tscore\tfiles\tsources\ttests\ttodos\n"
                + "2026-01-01T00:00:00Z\tdemo\t101\t1\t1\t0\t0\n");
        assertTrue(assertThrows(java.io.IOException.class, () -> store.read(invalidScore)).getMessage().contains("健康分"));
    }

    @Test void refusesDifferentProjectAndBackwardTimeWithoutChangingHistory() throws Exception {
        var file = dir.resolve("protected.tsv");
        var store = new SnapshotStore();
        store.append(file, new HealthSnapshot(Instant.parse("2026-01-02T00:00:00Z"), "alpha", 80, 1, 1, 0, 0));
        String original = Files.readString(file);

        assertThrows(java.io.IOException.class, () -> store.append(file,
                new HealthSnapshot(Instant.parse("2026-01-03T00:00:00Z"), "beta", 80, 1, 1, 0, 0)));
        assertEquals(original, Files.readString(file));
        assertThrows(java.io.IOException.class, () -> store.append(file,
                new HealthSnapshot(Instant.parse("2026-01-01T00:00:00Z"), "alpha", 80, 1, 1, 0, 0)));
        assertEquals(original, Files.readString(file));
    }
}

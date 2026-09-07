package local.agent;

import local.agent.report.BuildEvidenceStore;
import local.agent.verification.BuildVerification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BuildEvidenceStoreTest {
    @TempDir Path temp;

    @Test void preservesLatestFilesAndAppendOnlyHistoricalEvidence() throws Exception {
        var store = new BuildEvidenceStore();
        var first = new BuildVerification(BuildVerification.Status.FAILED, List.of("tool", "test"), 9,
                Duration.ofMillis(42), "first failure");
        var second = new BuildVerification(BuildVerification.Status.PASSED, List.of("tool", "test"), 0,
                Duration.ofMillis(21), "later success");

        Instant sameTime = Instant.parse("2026-09-08T02:00:00Z");
        var firstPaths = store.save(temp, first, sameTime);
        var secondPaths = store.save(temp, second, sameTime);

        assertEquals("later success", Files.readString(secondPaths.latestLog()));
        assertEquals("first failure", Files.readString(firstPaths.archivedLog()));
        assertEquals("later success", Files.readString(secondPaths.archivedLog()));
        assertNotEquals(firstPaths.archivedLog(), secondPaths.archivedLog());
        var rows = Files.readAllLines(secondPaths.history());
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).contains("\"status\":\"FAILED\""));
        assertTrue(rows.get(1).contains("\"status\":\"PASSED\""));
        assertFalse(rows.get(0).contains("first failure"));
        assertTrue(rows.get(0).contains("\"log\":\"build-logs/"));
    }
}

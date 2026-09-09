package local.agent.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

final class AgentMemoryStoreTest {
    @TempDir Path temporary;

    @Test void atomicallyPersistsAndReadsRecentEntriesBoundToWorkspace() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("project"));
        Path state = temporary.resolve("state");
        var store = new AgentMemoryStore(project, state);
        store.append(new AgentMemoryEntry(Instant.parse("2026-01-01T00:00:00Z"), "first", "answer one", 1, 0));
        store.append(new AgentMemoryEntry(Instant.parse("2026-01-02T00:00:00Z"), "second", "answer two", 2, 1));
        var recent = store.readRecent(1);
        assertEquals(1, recent.size());
        assertEquals("second", recent.get(0).task());
        assertTrue(Files.readString(store.file()).contains(project.toRealPath().toString().replace("\\", "\\\\")));
        assertThrows(IllegalArgumentException.class, () -> store.append(new AgentMemoryEntry(
                Instant.parse("2025-12-31T00:00:00Z"), "older", "answer", 1, 0)));

        Path other = Files.createDirectories(temporary.resolve("other"));
        assertThrows(java.io.IOException.class, () -> new AgentMemoryStore(other, state).readRecent(5));
    }

    @Test void rejectsCorruptionAndOutOfOrderHistory() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("project"));
        var store = new AgentMemoryStore(project, temporary.resolve("state"));
        Files.createDirectories(store.file().getParent());
        Files.writeString(store.file(), "not-json", StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> store.readRecent(5));

        String workspace = local.agent.report.JsonReportWriter.quote(project.toRealPath().toString());
        Files.writeString(store.file(), "{\"schemaVersion\":1,\"workspace\":" + workspace + ",\"entries\":["
                + "{\"completedAt\":\"2026-01-02T00:00:00Z\",\"task\":\"a\",\"answer\":\"a\",\"modelRounds\":1,\"toolCalls\":0},"
                + "{\"completedAt\":\"2026-01-01T00:00:00Z\",\"task\":\"b\",\"answer\":\"b\",\"modelRounds\":1,\"toolCalls\":0}]}" , StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> store.readRecent(5));
    }

    @Test void keepsMemoryFileWithinBoundByDroppingOldestEntries() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("project"));
        var store = new AgentMemoryStore(project, temporary.resolve("state"));
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 20; i++) store.append(new AgentMemoryEntry(start.plusSeconds(i), "task-" + i,
                String.valueOf((char) ('a' + i)).repeat(70_000), 1, 0));
        assertTrue(Files.size(store.file()) <= 1024 * 1024);
        var entries = store.readRecent(100);
        assertTrue(entries.size() < 20);
        assertEquals("task-19", entries.get(entries.size() - 1).task());
    }

    @Test void idempotentRecoveryAppendSkipsOnlyEquivalentLastEntry() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("project"));
        var store = new AgentMemoryStore(project, temporary.resolve("state"));
        store.append(new AgentMemoryEntry(Instant.parse("2026-01-01T00:00:00Z"), "task", "answer", 2, 1));
        store.appendIfLastEquivalent(new AgentMemoryEntry(Instant.parse("2026-01-02T00:00:00Z"), "task", "answer", 2, 1));
        assertEquals(1, store.readRecent(100).size());
        store.appendIfLastEquivalent(new AgentMemoryEntry(Instant.parse("2026-01-03T00:00:00Z"), "task", "changed", 2, 1));
        assertEquals(2, store.readRecent(100).size());
    }
}

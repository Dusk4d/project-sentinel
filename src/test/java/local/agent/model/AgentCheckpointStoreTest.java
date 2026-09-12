package local.agent.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AgentCheckpointStoreTest {
    @TempDir Path temporary;

    @Test void atomicallyRoundTripsActiveCheckpointAndMarksCompletion() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("project"));
        var store = new AgentCheckpointStore(workspace, temporary.resolve("state"));
        var value = new AgentCheckpoint("检查启动方式", 1, 1,
                List.of("{\"role\":\"user\",\"content\":\"检查启动方式\"}"),
                List.of(new AgentToolTrace(1, "read", true)));
        store.save(value);
        assertEquals(value, store.load("检查启动方式").orElseThrow());
        var active = AgentCheckpointStore.inspect(temporary.resolve("state"));
        assertTrue(active.present());
        assertFalse(active.completed());
        assertEquals("检查启动方式", active.task());
        assertEquals(1, active.completedRounds());
        assertEquals(1, active.toolCalls());
        assertTrue(assertThrows(java.io.IOException.class, () -> store.load("另一个任务"))
                .getMessage().contains("其他未完成"));
        store.markCompleted("检查启动方式");
        assertTrue(store.load("新任务").isEmpty());
        assertTrue(AgentCheckpointStore.inspect(temporary.resolve("state")).completed());
        assertTrue(Files.readString(store.file()).contains("\"completed\":true"));
    }

    @Test void reportsAbsentCheckpointWithoutCreatingStateDirectory() throws Exception {
        Path state = temporary.resolve("missing-state");
        assertFalse(AgentCheckpointStore.inspect(state).present());
        assertFalse(Files.exists(state));
    }

    @Test void loadsLegacyTraceWithoutInputSummary() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("legacy-project"));
        var store = new AgentCheckpointStore(workspace, temporary.resolve("legacy-state"));
        store.save(new AgentCheckpoint("legacy", 1, 1,
                List.of("{\"role\":\"user\",\"content\":\"legacy\"}"),
                List.of(new AgentToolTrace(1, "read", true))));
        String legacy = Files.readString(store.file()).replace("\"input\":\"\",", "")
                .replace(",\"evidence\":true", "");
        Files.writeString(store.file(), legacy);
        assertEquals("", store.load("legacy").orElseThrow().trace().getFirst().input());
        assertTrue(store.load("legacy").orElseThrow().trace().getFirst().evidence());
    }

    @Test void persistsFinalResultUntilCallerCommitsMemory() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("result-project"));
        var store = new AgentCheckpointStore(workspace, temporary.resolve("result-state"));
        var result = new ToolCallingAgentResult("最终回答", 2, 1,
                List.of(new AgentToolTrace(1, "read", true)));
        store.savePendingResult("task", result,
                List.of("{\"role\":\"user\",\"content\":\"task\"}"));
        assertEquals(result, store.loadPendingResult("task").orElseThrow());
        var status = AgentCheckpointStore.inspect(temporary.resolve("result-state"));
        assertTrue(status.ready());
        assertFalse(status.completed());
        store.markCompleted("task");
        assertTrue(store.loadPendingResult("task").isEmpty());
    }

    @Test void rejectsCheckpointOwnedByAnotherWorkspace() throws Exception {
        Path first = Files.createDirectory(temporary.resolve("first"));
        Path second = Files.createDirectory(temporary.resolve("second"));
        Path state = temporary.resolve("state");
        new AgentCheckpointStore(first, state).save(new AgentCheckpoint("task", 0, 0,
                List.of("{\"role\":\"user\",\"content\":\"task\"}"), List.of()));
        assertTrue(assertThrows(java.io.IOException.class,
                () -> new AgentCheckpointStore(second, state).load("task"))
                .getMessage().contains("其他工作区"));
    }

    @Test void rejectsTamperedSystemMessage() throws Exception {
        Path workspace = Files.createDirectory(temporary.resolve("project"));
        var store = new AgentCheckpointStore(workspace, temporary.resolve("state"));
        store.save(new AgentCheckpoint("task", 0, 0,
                List.of("{\"role\":\"user\",\"content\":\"task\"}"), List.of()));
        String tampered = Files.readString(store.file()).replace("\"role\":\"user\"", "\"role\":\"system\"");
        Files.writeString(store.file(), tampered);
        assertTrue(assertThrows(java.io.IOException.class, () -> store.load("task"))
                .getMessage().contains("消息角色"));
    }
}

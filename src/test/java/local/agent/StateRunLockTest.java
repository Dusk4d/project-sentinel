package local.agent;

import local.agent.state.RunAlreadyActiveException;
import local.agent.state.StateRunLock;
import local.agent.state.RunStatusInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class StateRunLockTest {
    @TempDir Path temp;

    @Test void rejectsOverlapAndAllowsNextRunAfterRelease() throws Exception {
        Path state = temp.resolve("state");
        try (var first = StateRunLock.acquire(state)) {
            var error = assertThrows(RunAlreadyActiveException.class, () -> StateRunLock.acquire(state));
            assertTrue(error.getMessage().contains("已有任务运行"));
            assertTrue(Files.isRegularFile(state.resolve(".workspace-agent.lock")));
        }
        assertDoesNotThrow(() -> {
            try (var ignored = StateRunLock.acquire(state)) { }
        });
    }

    @Test void reportsOwnerMetadataWhileActiveAndLastRunWhenIdle() throws Exception {
        Path state = temp.resolve("observable");
        var inspector = new RunStatusInspector();
        assertFalse(inspector.inspect(state).active());
        try (var ignored = StateRunLock.acquire(state, "daily-verify")) {
            var running = inspector.inspect(state);
            assertTrue(running.active());
            assertEquals(ProcessHandle.current().pid(), running.metadata().processId());
            assertEquals("daily-verify", running.metadata().operation());
            assertNotNull(running.metadata().startedAt());
        }
        var idle = inspector.inspect(state);
        assertFalse(idle.active());
        assertEquals("daily-verify", idle.metadata().operation());
    }

    @Test void differentStateDirectoriesDoNotBlockEachOther() throws Exception {
        try (var first = StateRunLock.acquire(temp.resolve("one"));
             var second = StateRunLock.acquire(temp.resolve("two"))) {
            assertNotNull(first);
            assertNotNull(second);
        }
    }

    @Test void rejectsLockHeldByAnotherJvm() throws Exception {
        Path state = temp.resolve("cross-process");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(java, "-cp", classPath,
                "local.agent.verification.LockHolderFixture", state.toString(), "10000")
                .redirectErrorStream(true).start();
        try {
            var ready = child.inputReader().readLine();
            assertEquals("LOCKED", ready);
            assertThrows(RunAlreadyActiveException.class, () -> StateRunLock.acquire(state));
            var status = new RunStatusInspector().inspect(state);
            assertTrue(status.active());
            assertNotNull(status.metadata());
            assertTrue(status.metadata().processId() > 0);
        } finally {
            child.destroy();
            if (!child.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) child.destroyForcibly();
        }
    }
}

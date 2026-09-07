package local.agent;

import local.agent.config.AnalyzerConfig;
import local.agent.config.ConfigInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigInitializerTest {
    @TempDir Path temp;

    @Test void createsValidConfigurationWithoutOverwritingIt() throws Exception {
        var initializer = new ConfigInitializer();
        var first = initializer.initialize(temp);
        assertTrue(first.created());
        assertEquals(temp.resolve(AnalyzerConfig.FILE_NAME), first.path());
        var parsed = AnalyzerConfig.load(temp);
        assertEquals(10_000, parsed.maxFiles());
        assertEquals(262_144, parsed.maxTextBytes());
        assertTrue(parsed.ignoredDirectories().contains("coverage"));

        Files.writeString(first.path(), "scan.maxFiles=1234\n");
        var second = initializer.initialize(temp);
        assertFalse(second.created());
        assertEquals("scan.maxFiles=1234\n", Files.readString(first.path()));
    }

    @Test void rejectsMissingProjectDirectory() {
        assertThrows(Exception.class, () -> new ConfigInitializer().initialize(temp.resolve("missing")));
    }

    @Test void concurrentInitializationCreatesExactlyOnce() throws Exception {
        var initializer = new ConfigInitializer();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(i -> (java.util.concurrent.Callable<Boolean>) () -> initializer.initialize(temp).created())
                    .toList();
            long created = executor.invokeAll(tasks).stream().filter(future -> {
                try { return future.get(); }
                catch (Exception e) { throw new RuntimeException(e); }
            }).count();
            assertEquals(1, created);
        }
        assertDoesNotThrow(() -> AnalyzerConfig.load(temp));
        assertEquals(ConfigInitializer.TEMPLATE, Files.readString(temp.resolve(AnalyzerConfig.FILE_NAME)));
    }
}

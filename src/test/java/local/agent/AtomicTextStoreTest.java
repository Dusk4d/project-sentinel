package local.agent;

import local.agent.report.AtomicTextStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class AtomicTextStoreTest {
    @TempDir Path temp;

    @Test void createsParentsAndReplacesExistingContent() throws Exception {
        Path target = temp.resolve("nested/latest.txt");
        var store = new AtomicTextStore();
        assertEquals(target.toAbsolutePath(), store.write(target, "first"));
        assertEquals("first", Files.readString(target));
        store.write(target, "second");
        assertEquals("second", Files.readString(target));
        try (var files = Files.list(target.getParent())) {
            assertEquals(1, files.count());
        }
    }
}

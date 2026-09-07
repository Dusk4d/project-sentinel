package local.agent;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class WorkspaceGuardTest {
    @TempDir Path root;
    @Test void normalizesSafePathsAndRejectsEscape() {
        var guard = new WorkspaceGuard(root);
        assertEquals(root.resolve("b"), guard.resolve("a/../b"));
        assertThrows(SecurityException.class, () -> guard.resolve("../outside"));
        assertThrows(SecurityException.class, () -> guard.resolve(root.resolve("absolute").toString()));
    }

    @Test void rejectsSymbolicLinkThatEscapesWorkspace() throws Exception {
        Path workspace = Files.createDirectory(root.resolve("workspace"));
        Path outside = Files.writeString(root.resolve("outside.txt"), "secret");
        Path link = workspace.resolve("link.txt");
        try { Files.createSymbolicLink(link, outside); }
        catch (Exception e) { assumeTrue(false, "平台不允许创建符号链接: " + e.getMessage()); }
        var guard = new WorkspaceGuard(workspace);
        SecurityException error = assertThrows(SecurityException.class, () -> guard.resolve("link.txt"));
        assertTrue(error.getMessage().contains("工作区外部"));
    }
}

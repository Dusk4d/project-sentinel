package local.agent;

import local.agent.tools.SearchTextTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class SearchTextToolSecurityTest {
    @TempDir Path root;

    @Test void doesNotFollowFileSymlinkOutsideWorkspace() throws Exception {
        Path workspace = Files.createDirectory(root.resolve("workspace"));
        Path outside = Files.writeString(root.resolve("outside.txt"), "UNIQUE_EXTERNAL_SECRET");
        try { Files.createSymbolicLink(workspace.resolve("linked.txt"), outside); }
        catch (Exception unsupported) { assumeTrue(false, "平台不允许创建符号链接: " + unsupported.getMessage()); }

        ToolResult result = new SearchTextTool(new WorkspaceGuard(workspace)).execute("UNIQUE_EXTERNAL_SECRET");

        assertTrue(result.success());
        assertEquals("未找到匹配内容", result.output());
    }
}

package local.agent;

import local.agent.tools.SearchTextTool;
import local.agent.tools.ReadFileTool;
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

    @Test void readAndSearchExcludeSensitiveFilesAndGeneratedDirectories() throws Exception {
        Files.writeString(root.resolve(".env"), "ENV_ONLY_SECRET");
        Files.writeString(root.resolve("credentials.json"), "CREDENTIAL_ONLY_SECRET");
        Path target = Files.createDirectories(root.resolve("target/classes"));
        Files.writeString(target.resolve("generated.txt"), "BUILD_ONLY_SECRET");
        Files.writeString(root.resolve("Safe.java"), "class Safe { String marker = \"SAFE_SOURCE_MARKER\"; }");
        var guard = new WorkspaceGuard(root);
        var read = new ReadFileTool(guard);
        var search = new SearchTextTool(guard);

        assertFalse(read.execute(".env").success());
        assertFalse(read.execute("credentials.json").success());
        assertFalse(read.execute("target/classes/generated.txt").success());
        assertTrue(read.execute("Safe.java").success());

        assertFalse(search.execute("ENV_ONLY_SECRET").evidence());
        assertFalse(search.execute("CREDENTIAL_ONLY_SECRET").evidence());
        assertFalse(search.execute("BUILD_ONLY_SECRET").evidence());
        ToolResult safe = search.execute("SAFE_SOURCE_MARKER");
        assertTrue(safe.success());
        assertTrue(safe.evidence());
        assertTrue(safe.output().contains("Safe.java:1"));
    }
}

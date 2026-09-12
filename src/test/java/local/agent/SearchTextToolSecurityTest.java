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

    @Test void boundsLongSearchExcerptsAndTotalUtf8Output() throws Exception {
        for (int index = 0; index < 100; index++)
            Files.writeString(root.resolve("Long" + index + ".java"), "测".repeat(1_000) + " BOUND_QUERY tail");

        ToolResult result = new SearchTextTool(new WorkspaceGuard(root)).execute("BOUND_QUERY");

        assertTrue(result.success());
        assertTrue(result.evidence());
        assertTrue(result.output().contains("BOUND_QUERY"), "摘录必须围绕实际命中位置");
        assertTrue(result.output().contains("64 KiB 上限"), "总输出截断必须显式说明");
        assertTrue(result.output().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64 * 1024);
    }

    @Test void reportsHitLimitOnlyAfterProbingAnAdditionalMatch() throws Exception {
        String exactContent = ("LIMIT_QUERY\n").repeat(100);
        Path source = root.resolve("Matches.java");
        Files.writeString(source, exactContent);
        var search = new SearchTextTool(new WorkspaceGuard(root));

        ToolResult exact = search.execute("LIMIT_QUERY");
        assertFalse(exact.output().contains("后续命中已省略"), "恰好 100 条不得误报截断");

        Files.writeString(source, exactContent + "LIMIT_QUERY\n");
        ToolResult overflow = search.execute("LIMIT_QUERY");
        assertTrue(overflow.output().contains("后续命中已省略"), "确认第 101 条后必须报告截断");
    }
}

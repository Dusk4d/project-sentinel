package local.agent.tools;

import local.agent.Tool;
import local.agent.ToolResult;
import local.agent.WorkspaceGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ReadFileTool implements Tool {
    private static final long MAX_BYTES = 128 * 1024;
    private final WorkspaceGuard guard;
    public ReadFileTool(WorkspaceGuard guard) { this.guard = guard; }
    public String name() { return "read"; }
    public String description() { return "读取工作区内的小型文本文件"; }

    public ToolResult execute(String input) {
        try {
            var path = guard.resolve(input);
            if (!Files.isRegularFile(path)) return ToolResult.error("文件不存在: " + input);
            if (Files.size(path) > MAX_BYTES) return ToolResult.error("文件超过 128 KiB 限制");
            return ToolResult.ok(Files.readString(path, StandardCharsets.UTF_8));
        } catch (SecurityException | IOException e) { return ToolResult.error(e.getMessage()); }
    }
}

package local.agent.tools;

import local.agent.Tool;
import local.agent.ToolResult;
import local.agent.WorkspaceGuard;

import java.io.IOException;
import java.nio.file.Files;

public final class ListFilesTool implements Tool {
    private final WorkspaceGuard guard;
    public ListFilesTool(WorkspaceGuard guard) { this.guard = guard; }
    public String name() { return "list"; }
    public String description() { return "列出工作区目录中的文件"; }

    public ToolResult execute(String input) {
        try {
            var path = guard.resolve(input);
            if (!Files.isDirectory(path)) return ToolResult.error("不是目录: " + input);
            try (var stream = Files.list(path)) {
                String text = stream.sorted().limit(200)
                        .map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName())
                        .reduce((a, b) -> a + System.lineSeparator() + b).orElse("目录为空");
                return ToolResult.ok(text);
            }
        } catch (SecurityException | IOException e) { return ToolResult.error(e.getMessage()); }
    }
}

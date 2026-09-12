package local.agent.tools;

import local.agent.Tool;
import local.agent.ToolResult;
import local.agent.WorkspaceGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

public final class SearchTextTool implements Tool {
    private final WorkspaceGuard guard;
    public SearchTextTool(WorkspaceGuard guard) { this.guard = guard; }
    public String name() { return "search"; }
    public String description() { return "在工作区文本文件中搜索关键词"; }

    public ToolResult execute(String input) {
        String query = input == null ? "" : input.trim();
        if (query.isEmpty()) return ToolResult.error("搜索词不能为空");
        var hits = new ArrayList<String>();
        int[] files = {0};
        try {
            Files.walkFileTree(guard.root(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(java.nio.file.Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(guard.root()) && ToolFilePolicy.ignoredDirectory(directory))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFile(java.nio.file.Path file, BasicFileAttributes attributes) {
                    if (!isSafeRegularFile(file) || ToolFilePolicy.sensitive(file) || attributes.size() > 128 * 1024)
                        return FileVisitResult.CONTINUE;
                    if (++files[0] > 2_000 || hits.size() >= 100) return FileVisitResult.TERMINATE;
                try {
                    int lineNo = 0;
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                        lineNo++;
                        if (line.contains(query)) hits.add(guard.root().relativize(file) + ":" + lineNo + ": " + line.strip());
                        if (hits.size() >= 100) break;
                    }
                } catch (IOException ignored) { }
                    return hits.size() >= 100 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
            return hits.isEmpty() ? ToolResult.noEvidence("未找到匹配内容")
                    : ToolResult.ok(String.join(System.lineSeparator(), hits));
        } catch (IOException e) { return ToolResult.error(e.getMessage()); }
    }

    private boolean isSafeRegularFile(java.nio.file.Path file) {
        if (!Files.isRegularFile(file)) return false;
        try { return file.toRealPath().startsWith(guard.root()); }
        catch (IOException ignored) { return false; }
    }
}

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
    private static final int MAX_FILES = 2_000;
    private static final int MAX_HITS = 100;
    private static final int MAX_EXCERPT_CHARS = 500;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;
    private static final String OUTPUT_LIMIT_NOTICE = "搜索结果已达到数量或 64 KiB 上限，后续命中已省略";
    private final WorkspaceGuard guard;
    public SearchTextTool(WorkspaceGuard guard) { this.guard = guard; }
    public String name() { return "search"; }
    public String description() { return "在工作区文本文件中搜索关键词"; }

    public ToolResult execute(String input) {
        String query = input == null ? "" : input.trim();
        if (query.isEmpty()) return ToolResult.error("搜索词不能为空");
        var hits = new ArrayList<String>();
        int[] files = {0};
        int[] outputBytes = {0};
        boolean[] outputLimited = {false};
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
                    if (++files[0] > MAX_FILES) return FileVisitResult.TERMINATE;
                    try {
                        int lineNo = 0;
                        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                            lineNo++;
                            if (line.contains(query)) {
                                if (hits.size() == MAX_HITS) {
                                    outputLimited[0] = true;
                                    return FileVisitResult.TERMINATE;
                                }
                                String hit = guard.root().relativize(file) + ":" + lineNo + ": " + excerpt(line, query);
                                int separator = hits.isEmpty() ? 0 : System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;
                                int required = separator + hit.getBytes(StandardCharsets.UTF_8).length;
                                int notice = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length
                                        + OUTPUT_LIMIT_NOTICE.getBytes(StandardCharsets.UTF_8).length;
                                if (outputBytes[0] + required + notice > MAX_OUTPUT_BYTES) {
                                    outputLimited[0] = true;
                                    return FileVisitResult.TERMINATE;
                                }
                                hits.add(hit);
                                outputBytes[0] += required;
                            }
                        }
                    } catch (IOException ignored) { }
                    return FileVisitResult.CONTINUE;
                }

                @Override public FileVisitResult visitFileFailed(java.nio.file.Path file, IOException exception) {
                    return FileVisitResult.CONTINUE;
                }
            });
            if (hits.isEmpty()) return ToolResult.noEvidence(outputLimited[0]
                    ? "搜索命中无法在 64 KiB 输出上限内安全返回" : "未找到匹配内容");
            String output = String.join(System.lineSeparator(), hits);
            if (outputLimited[0]) output += System.lineSeparator() + OUTPUT_LIMIT_NOTICE;
            return ToolResult.ok(output);
        } catch (IOException e) { return ToolResult.error(e.getMessage()); }
    }

    private String excerpt(String line, String query) {
        String normalized = line.replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").strip();
        if (normalized.length() <= MAX_EXCERPT_CHARS) return normalized;
        int match = Math.max(0, normalized.indexOf(query));
        int contentLimit = MAX_EXCERPT_CHARS - 2;
        int start = Math.max(0, match - contentLimit / 3);
        int end = Math.min(normalized.length(), start + contentLimit);
        start = Math.max(0, end - contentLimit);
        return (start > 0 ? "…" : "") + normalized.substring(start, end) + (end < normalized.length() ? "…" : "");
    }

    private boolean isSafeRegularFile(java.nio.file.Path file) {
        if (!Files.isRegularFile(file)) return false;
        try { return file.toRealPath().startsWith(guard.root()); }
        catch (IOException ignored) { return false; }
    }
}

package local.agent.model;

import local.agent.report.AtomicTextStore;
import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AgentMemoryStore {
    public static final String FILE_NAME = "agent-memory.json";
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 100;
    private static final int MAX_TASK_CHARS = 8 * 1024;
    private static final int MAX_ANSWER_CHARS = 64 * 1024;
    private final String workspace;
    private final Path file;

    public AgentMemoryStore(Path workspace, Path stateDirectory) throws IOException {
        this.workspace = workspace.toRealPath().toString();
        this.file = stateDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    public Path file() { return file; }

    public List<AgentMemoryEntry> readRecent(int maximum) throws IOException {
        if (maximum < 0 || maximum > MAX_ENTRIES) throw new IllegalArgumentException("记忆读取数量必须在 0 到 100 之间");
        List<AgentMemoryEntry> all = readAll();
        return List.copyOf(all.subList(Math.max(0, all.size() - maximum), all.size()));
    }

    public Path append(AgentMemoryEntry entry) throws IOException {
        if (entry == null || entry.completedAt() == null || entry.task() == null || entry.answer() == null)
            throw new IllegalArgumentException("记忆记录字段不能为空");
        if (entry.task().length() > MAX_TASK_CHARS) throw new IllegalArgumentException("记忆任务超过 8192 字符限制");
        if (entry.modelRounds() < 0 || entry.modelRounds() > 100 || entry.toolCalls() < 0 || entry.toolCalls() > 100)
            throw new IllegalArgumentException("记忆轮次或工具调用数超出范围");
        String answer = truncate(entry.answer(), MAX_ANSWER_CHARS);
        var all = new ArrayList<>(readAll());
        if (!all.isEmpty() && entry.completedAt().isBefore(all.get(all.size() - 1).completedAt()))
            throw new IllegalArgumentException("新记忆时间不能早于已有记录");
        all.add(new AgentMemoryEntry(entry.completedAt(), entry.task(), answer, entry.modelRounds(), entry.toolCalls()));
        if (all.size() > MAX_ENTRIES) all = new ArrayList<>(all.subList(all.size() - MAX_ENTRIES, all.size()));
        String content = render(all);
        while (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES && all.size() > 1) {
            all.remove(0);
            content = render(all);
        }
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IOException("Agent 记忆记录超过 1 MiB 限制");
        return new AtomicTextStore().write(file, content);
    }

    private List<AgentMemoryEntry> readAll() throws IOException {
        if (!Files.exists(file)) return List.of();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) throw new IOException("Agent 记忆文件无效或超过 1 MiB 限制");
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, Object> root = JsonCodec.object(JsonCodec.parse(json), "记忆根值");
        if (number(root.get("schemaVersion"), "schemaVersion") != 1) throw new IOException("不支持的 Agent 记忆版本");
        if (!workspace.equals(string(root.get("workspace"), "workspace"))) throw new IOException("Agent 记忆属于其他工作区");
        if (!(root.get("entries") instanceof List<?> entries) || entries.size() > MAX_ENTRIES)
            throw new IOException("Agent 记忆 entries 无效或超过 100 条限制");
        var result = new ArrayList<AgentMemoryEntry>();
        Instant previous = null;
        for (Object value : entries) {
            Map<String, Object> item = JsonCodec.object(value, "entries[]");
            Instant completed;
            try { completed = Instant.parse(string(item.get("completedAt"), "completedAt")); }
            catch (DateTimeParseException invalid) { throw new IOException("Agent 记忆时间无效", invalid); }
            if (previous != null && completed.isBefore(previous)) throw new IOException("Agent 记忆时间顺序无效");
            String task = string(item.get("task"), "task");
            String answer = string(item.get("answer"), "answer");
            if (task.length() > MAX_TASK_CHARS || answer.length() > MAX_ANSWER_CHARS) throw new IOException("Agent 记忆字段超过限制");
            result.add(new AgentMemoryEntry(completed, task, answer,
                    integer(item.get("modelRounds"), "modelRounds"), integer(item.get("toolCalls"), "toolCalls")));
            previous = completed;
        }
        return List.copyOf(result);
    }

    private String render(List<AgentMemoryEntry> entries) {
        var out = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"workspace\": ")
                .append(JsonReportWriter.quote(workspace)).append(",\n  \"entries\": [");
        for (int i = 0; i < entries.size(); i++) {
            AgentMemoryEntry entry = entries.get(i);
            if (i > 0) out.append(',');
            out.append("\n    {\"completedAt\":").append(JsonReportWriter.quote(entry.completedAt().toString()))
                    .append(",\"task\":").append(JsonReportWriter.quote(entry.task()))
                    .append(",\"answer\":").append(JsonReportWriter.quote(entry.answer()))
                    .append(",\"modelRounds\":").append(entry.modelRounds())
                    .append(",\"toolCalls\":").append(entry.toolCalls()).append('}');
        }
        if (!entries.isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }

    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 16) + "\n...[已截断]";
    }
    private static String string(Object value, String name) throws IOException {
        if (!(value instanceof String text)) throw new IOException("Agent 记忆字段 " + name + " 必须是字符串");
        return text;
    }
    private static long number(Object value, String name) throws IOException {
        if (!(value instanceof Number number)) throw new IOException("Agent 记忆字段 " + name + " 必须是数字");
        double decimal = number.doubleValue();
        long integer = number.longValue();
        if (!Double.isFinite(decimal) || decimal != integer) throw new IOException("Agent 记忆字段 " + name + " 必须是整数");
        return integer;
    }
    private static int integer(Object value, String name) throws IOException {
        long result = number(value, name);
        if (result < 0 || result > 100) throw new IOException("Agent 记忆字段 " + name + " 超出范围");
        return (int) result;
    }
}

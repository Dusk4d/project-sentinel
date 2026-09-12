package local.agent.model;

import local.agent.report.AtomicTextStore;
import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentCheckpointStore {
    public static final String FILE_NAME = "agent-checkpoint.json";
    private static final long MAX_BYTES = 1024 * 1024;
    private static final int MAX_MESSAGES = 64;
    private static final int MAX_ANSWER_CHARS = 64 * 1024;
    private final String workspace;
    private final Path file;

    public AgentCheckpointStore(Path workspace, Path stateDirectory) throws IOException {
        this.workspace = workspace.toRealPath().toString();
        this.file = stateDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
    }

    public Path file() { return file; }

    public static AgentCheckpointStatus inspect(Path stateDirectory) throws IOException {
        Path file = stateDirectory.toAbsolutePath().normalize().resolve(FILE_NAME);
        if (!Files.exists(file)) return AgentCheckpointStatus.absent();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Agent 检查点无效或超过 1 MiB 限制");
        Map<String, Object> root = JsonCodec.object(JsonCodec.parse(Files.readString(file, StandardCharsets.UTF_8)), "检查点根值");
        if (number(root.get("schemaVersion"), "schemaVersion") != 1) throw new IOException("不支持的 Agent 检查点版本");
        String workspace = string(root.get("workspace"), "workspace");
        String task = string(root.get("task"), "task");
        boolean completed = bool(root.get("completed"), "completed");
        boolean ready = !completed && optionalBool(root.get("ready"), "ready");
        int rounds = completed ? 0 : bounded(root.get("completedRounds"), "completedRounds", 0, 5);
        int calls = completed ? 0 : bounded(root.get("toolCalls"), "toolCalls", 0, 20);
        return new AgentCheckpointStatus(true, completed, ready, workspace, task, rounds, calls);
    }

    public Optional<AgentCheckpoint> load(String task) throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Agent 检查点无效或超过 1 MiB 限制");
        Map<String, Object> root = JsonCodec.object(JsonCodec.parse(Files.readString(file, StandardCharsets.UTF_8)), "检查点根值");
        if (number(root.get("schemaVersion"), "schemaVersion") != 1) throw new IOException("不支持的 Agent 检查点版本");
        if (!workspace.equals(string(root.get("workspace"), "workspace"))) throw new IOException("Agent 检查点属于其他工作区");
        if (bool(root.get("completed"), "completed")) return Optional.empty();
        String savedTask = string(root.get("task"), "task");
        if (!savedTask.equals(task)) throw new IOException("存在其他未完成 Agent 任务，请使用原任务恢复");
        if (optionalBool(root.get("ready"), "ready")) return Optional.empty();
        int rounds = bounded(root.get("completedRounds"), "completedRounds", 0, 5);
        int calls = bounded(root.get("toolCalls"), "toolCalls", 0, 20);
        if (!(root.get("messages") instanceof List<?> messageValues) || messageValues.size() > MAX_MESSAGES)
            throw new IOException("Agent 检查点 messages 无效");
        var messages = new ArrayList<String>();
        for (Object value : messageValues) {
            Map<String, Object> message = JsonCodec.object(value, "messages[]");
            String role = string(message.get("role"), "messages[].role");
            if (!role.equals("user") && !role.equals("assistant") && !role.equals("tool"))
                throw new IOException("Agent 检查点包含不允许的消息角色");
            if (role.equals("tool")) {
                string(message.get("tool_call_id"), "messages[].tool_call_id");
                string(message.get("content"), "messages[].content");
            } else if (role.equals("user")) {
                string(message.get("content"), "messages[].content");
            } else if (!(message.get("content") instanceof String) && !(message.get("tool_calls") instanceof List<?>)) {
                throw new IOException("Agent 检查点 assistant 消息缺少内容或工具调用");
            }
            messages.add(JsonCodec.write(message));
        }
        if (!(root.get("trace") instanceof List<?> traceValues) || traceValues.size() > 20)
            throw new IOException("Agent 检查点 trace 无效");
        var trace = new ArrayList<AgentToolTrace>();
        for (Object value : traceValues) {
            Map<String, Object> item = JsonCodec.object(value, "trace[]");
            boolean success = bool(item.get("success"), "success");
            trace.add(new AgentToolTrace(bounded(item.get("sequence"), "sequence", 1, 20),
                    string(item.get("name"), "name"), optionalString(item.get("input"), "input"), success,
                    item.get("evidence") == null ? success : bool(item.get("evidence"), "evidence")));
        }
        if (trace.size() != calls) throw new IOException("Agent 检查点工具计数不一致");
        return Optional.of(new AgentCheckpoint(savedTask, rounds, calls, messages, trace));
    }

    public Optional<ToolCallingAgentResult> loadPendingResult(String task) throws IOException {
        if (!Files.exists(file)) return Optional.empty();
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES)
            throw new IOException("Agent 检查点无效或超过 1 MiB 限制");
        Map<String, Object> root = JsonCodec.object(JsonCodec.parse(Files.readString(file, StandardCharsets.UTF_8)), "检查点根值");
        if (number(root.get("schemaVersion"), "schemaVersion") != 1) throw new IOException("不支持的 Agent 检查点版本");
        if (!workspace.equals(string(root.get("workspace"), "workspace"))) throw new IOException("Agent 检查点属于其他工作区");
        if (bool(root.get("completed"), "completed")) return Optional.empty();
        String savedTask = string(root.get("task"), "task");
        if (!savedTask.equals(task)) throw new IOException("存在其他未完成 Agent 任务，请使用原任务恢复");
        if (!optionalBool(root.get("ready"), "ready")) return Optional.empty();
        String answer = string(root.get("answer"), "answer");
        if (answer.isBlank() || answer.length() > MAX_ANSWER_CHARS) throw new IOException("Agent 检查点最终回答无效或超过限制");
        int rounds = bounded(root.get("completedRounds"), "completedRounds", 1, 5);
        int calls = bounded(root.get("toolCalls"), "toolCalls", 0, 20);
        List<AgentToolTrace> trace = parseTrace(root, calls);
        return Optional.of(new ToolCallingAgentResult(answer, rounds, calls, trace));
    }

    public Path save(AgentCheckpoint checkpoint) throws IOException {
        if (checkpoint.messages().size() > MAX_MESSAGES || checkpoint.trace().size() != checkpoint.toolCalls())
            throw new IOException("Agent 检查点内容超出限制或计数不一致");
        return write(render(checkpoint, false, null));
    }

    public Path savePendingResult(String task, ToolCallingAgentResult result, List<String> messages) throws IOException {
        if (result.answer().length() > MAX_ANSWER_CHARS) throw new IOException("Agent 最终回答超过 65536 字符检查点限制");
        if (messages.size() > MAX_MESSAGES) throw new IOException("Agent 检查点消息超过 64 条限制");
        var checkpoint = new AgentCheckpoint(task, result.modelRounds(), result.toolCalls(), messages, result.trace());
        return write(render(checkpoint, true, result.answer()));
    }

    public Path markCompleted(String task) throws IOException {
        return write("{\n  \"schemaVersion\":1,\n  \"workspace\":" + JsonReportWriter.quote(workspace)
                + ",\n  \"completed\":true,\n  \"task\":" + JsonReportWriter.quote(task) + "\n}\n");
    }

    private Path write(String content) throws IOException {
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("Agent 检查点超过 1 MiB 限制");
        return new AtomicTextStore().write(file, content);
    }

    private String render(AgentCheckpoint value, boolean ready, String answer) {
        var out = new StringBuilder("{\n  \"schemaVersion\":1,\n  \"workspace\":")
                .append(JsonReportWriter.quote(workspace)).append(",\n  \"completed\":false")
                .append(",\n  \"ready\":").append(ready)
                .append(",\n  \"task\":").append(JsonReportWriter.quote(value.task()))
                .append(",\n  \"completedRounds\":").append(value.completedRounds())
                .append(",\n  \"toolCalls\":").append(value.toolCalls()).append(",\n  \"messages\":[");
        for (int i = 0; i < value.messages().size(); i++) {
            if (i > 0) out.append(',');
            out.append(value.messages().get(i));
        }
        out.append("],\n  \"trace\":[");
        for (int i = 0; i < value.trace().size(); i++) {
            if (i > 0) out.append(',');
            AgentToolTrace item = value.trace().get(i);
            out.append("{\"sequence\":").append(item.sequence()).append(",\"name\":")
                    .append(JsonReportWriter.quote(item.name())).append(",\"input\":")
                    .append(JsonReportWriter.quote(item.input())).append(",\"success\":").append(item.success())
                    .append(",\"evidence\":").append(item.evidence()).append('}');
        }
        out.append(']');
        if (answer != null) out.append(",\n  \"answer\":").append(JsonReportWriter.quote(answer));
        return out.append("\n}\n").toString();
    }

    private static String string(Object value, String name) throws IOException {
        if (!(value instanceof String text)) throw new IOException("Agent 检查点字段 " + name + " 必须是字符串");
        return text;
    }
    private static boolean bool(Object value, String name) throws IOException {
        if (!(value instanceof Boolean result)) throw new IOException("Agent 检查点字段 " + name + " 必须是布尔值");
        return result;
    }
    private static boolean optionalBool(Object value, String name) throws IOException {
        return value == null ? false : bool(value, name);
    }
    private static String optionalString(Object value, String name) throws IOException {
        return value == null ? "" : string(value, name);
    }

    private static List<AgentToolTrace> parseTrace(Map<String, Object> root, int calls) throws IOException {
        if (!(root.get("trace") instanceof List<?> traceValues) || traceValues.size() > 20)
            throw new IOException("Agent 检查点 trace 无效");
        var trace = new ArrayList<AgentToolTrace>();
        for (Object value : traceValues) {
            Map<String, Object> item = JsonCodec.object(value, "trace[]");
            boolean success = bool(item.get("success"), "success");
            trace.add(new AgentToolTrace(bounded(item.get("sequence"), "sequence", 1, 20),
                    string(item.get("name"), "name"), optionalString(item.get("input"), "input"), success,
                    item.get("evidence") == null ? success : bool(item.get("evidence"), "evidence")));
        }
        if (trace.size() != calls) throw new IOException("Agent 检查点工具计数不一致");
        return List.copyOf(trace);
    }
    private static long number(Object value, String name) throws IOException {
        if (!(value instanceof Number number)) throw new IOException("Agent 检查点字段 " + name + " 必须是数字");
        double decimal = number.doubleValue(); long integer = number.longValue();
        if (!Double.isFinite(decimal) || decimal != integer) throw new IOException("Agent 检查点字段 " + name + " 必须是整数");
        return integer;
    }
    private static int bounded(Object value, String name, int minimum, int maximum) throws IOException {
        long result = number(value, name);
        if (result < minimum || result > maximum) throw new IOException("Agent 检查点字段 " + name + " 超出范围");
        return (int) result;
    }
}

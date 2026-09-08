package local.agent.function;

import local.agent.Tool;
import local.agent.ToolResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FunctionRegistry {
    private final Map<String, RegisteredFunction> functions = new LinkedHashMap<>();

    public void register(Tool tool, String inputDescription) {
        if (functions.containsKey(tool.name())) throw new IllegalArgumentException("重复工具名: " + tool.name());
        var definition = new FunctionDefinition(tool.name(), tool.description(), inputDescription);
        functions.put(tool.name(), new RegisteredFunction(definition, tool));
    }

    public List<FunctionDefinition> definitions() {
        return functions.values().stream().map(RegisteredFunction::definition).toList();
    }

    public String definitionsJson() {
        return "{\n  \"schemaVersion\": 1,\n  \"tools\": [\n    "
                + String.join(",\n    ", definitions().stream().map(FunctionDefinition::toJson).toList())
                + "\n  ]\n}\n";
    }

    public String toolsJson() {
        return "[" + String.join(",", definitions().stream().map(FunctionDefinition::toJson).toList()) + "]";
    }

    public FunctionCallResult call(String callId, String name, String input) {
        String stableId = callId == null || callId.isBlank() ? "call_" + UUID.randomUUID() : callId;
        RegisteredFunction registered = functions.get(name);
        if (registered == null) return new FunctionCallResult(1, stableId, name, false, "未知工具: " + name);
        ToolResult result;
        try { result = registered.tool().execute(input == null ? "" : input); }
        catch (RuntimeException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            result = ToolResult.error(message);
        }
        return new FunctionCallResult(1, stableId, name, result.success(), result.output());
    }

    private record RegisteredFunction(FunctionDefinition definition, Tool tool) { }
}

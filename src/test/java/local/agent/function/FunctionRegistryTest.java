package local.agent.function;

import local.agent.Tool;
import local.agent.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FunctionRegistryTest {
    @Test void publishesJsonSchemaAndReturnsStructuredCallResult() {
        var registry = new FunctionRegistry();
        registry.register(tool("echo", input -> ToolResult.ok("seen: " + input)), "要回显的文本");

        String schema = registry.definitionsJson();
        assertTrue(schema.contains("\"type\":\"function\""));
        assertTrue(schema.contains("\"additionalProperties\":false"));
        assertTrue(schema.contains("\"required\":[\"input\"]"));

        var result = registry.call("call-1", "echo", "中文\ntext");
        assertTrue(result.success());
        assertEquals("call-1", result.callId());
        assertTrue(result.toJson().contains("中文\\ntext"));
    }

    @Test void rejectsDuplicateAndUnknownFunctionsWithoutThrowingFromCall() {
        var registry = new FunctionRegistry();
        registry.register(tool("one", ToolResult::ok), "输入");
        assertThrows(IllegalArgumentException.class, () -> registry.register(tool("one", ToolResult::ok), "输入"));
        var missing = registry.call("call-2", "missing", "x");
        assertFalse(missing.success());
        assertTrue(missing.output().contains("未知工具"));
    }

    private static Tool tool(String name, java.util.function.Function<String, ToolResult> action) {
        return new Tool() {
            public String name() { return name; }
            public String description() { return "test tool"; }
            public ToolResult execute(String input) { return action.apply(input); }
        };
    }
}

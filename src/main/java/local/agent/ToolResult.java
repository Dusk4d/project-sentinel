package local.agent;

public record ToolResult(boolean success, String output, boolean evidence) {
    public ToolResult(boolean success, String output) { this(success, output, success); }
    public static ToolResult ok(String output) { return new ToolResult(true, output, true); }
    public static ToolResult noEvidence(String output) { return new ToolResult(true, output, false); }
    public static ToolResult error(String output) { return new ToolResult(false, output, false); }
}

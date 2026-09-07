package local.agent;

public interface Tool {
    String name();
    String description();
    ToolResult execute(String input);
}

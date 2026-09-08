package local.agent.model;

public record ToolCallingAgentResult(String answer, int modelRounds, int toolCalls) {
    public String renderText() {
        return "# Function Calling Agent 回答\n\n" + answer + "\n\n模型轮次：" + modelRounds + "，工具调用：" + toolCalls + "\n";
    }
}

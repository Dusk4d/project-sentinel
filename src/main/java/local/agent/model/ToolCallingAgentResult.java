package local.agent.model;

import local.agent.report.JsonReportWriter;

public record ToolCallingAgentResult(String answer, int modelRounds, int toolCalls) {
    public String renderText() {
        return "# Function Calling Agent 回答\n\n" + answer + "\n\n模型轮次：" + modelRounds + "，工具调用：" + toolCalls + "\n";
    }

    public String toJson() {
        return "{\"schemaVersion\":1,\"answer\":" + JsonReportWriter.quote(answer)
                + ",\"modelRounds\":" + modelRounds + ",\"toolCalls\":" + toolCalls + "}\n";
    }
}

package local.agent.model;

import local.agent.report.JsonReportWriter;

import java.util.List;

public record ToolCallingAgentResult(String answer, int modelRounds, int toolCalls, List<AgentToolTrace> trace) {
    public ToolCallingAgentResult {
        trace = List.copyOf(trace);
        if (toolCalls != trace.size()) throw new IllegalArgumentException("工具调用数与轨迹长度不一致");
    }

    public String renderText() {
        var out = new StringBuilder("# Function Calling Agent 回答\n\n").append(answer)
                .append("\n\n模型轮次：").append(modelRounds).append("，工具调用：").append(toolCalls).append('\n');
        if (!trace.isEmpty()) {
            out.append("\n工具轨迹：\n");
            for (AgentToolTrace item : trace) out.append("- ").append(item.sequence()).append(". ")
                    .append(item.name()).append(item.success() ? " [成功]\n" : " [失败]\n");
        }
        return out.toString();
    }

    public String toJson() {
        var out = new StringBuilder("{\"schemaVersion\":1,\"answer\":").append(JsonReportWriter.quote(answer))
                .append(",\"modelRounds\":").append(modelRounds).append(",\"toolCalls\":").append(toolCalls)
                .append(",\"trace\":[");
        for (int i = 0; i < trace.size(); i++) {
            if (i > 0) out.append(',');
            AgentToolTrace item = trace.get(i);
            out.append("{\"sequence\":").append(item.sequence()).append(",\"name\":")
                    .append(JsonReportWriter.quote(item.name())).append(",\"success\":").append(item.success()).append('}');
        }
        return out.append("]}\n").toString();
    }
}

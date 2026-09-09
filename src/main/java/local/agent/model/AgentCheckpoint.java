package local.agent.model;

import java.util.List;

public record AgentCheckpoint(String task, int completedRounds, int toolCalls,
                              List<String> messages, List<AgentToolTrace> trace) {
    public AgentCheckpoint {
        messages = List.copyOf(messages);
        trace = List.copyOf(trace);
    }
}

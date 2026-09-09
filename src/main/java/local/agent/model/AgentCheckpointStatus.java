package local.agent.model;

public record AgentCheckpointStatus(boolean present, boolean completed, boolean ready, String workspace, String task,
                                    int completedRounds, int toolCalls) {
    public static AgentCheckpointStatus absent() {
        return new AgentCheckpointStatus(false, false, false, "", "", 0, 0);
    }
}

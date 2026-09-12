package local.agent.model;

public record AgentToolTrace(int sequence, String name, String input, boolean success, boolean evidence) {
    public AgentToolTrace {
        input = input == null ? "" : input;
    }

    public AgentToolTrace(int sequence, String name, String input, boolean success) {
        this(sequence, name, input, success, success);
    }

    public AgentToolTrace(int sequence, String name, boolean success) {
        this(sequence, name, "", success, success);
    }
}

package local.agent.analysis;

public record Finding(String ruleId, Severity severity, String category, String message, String evidence, String action) {
    public Finding {
        if (ruleId == null || !ruleId.matches("[a-z][a-z0-9.-]*"))
            throw new IllegalArgumentException("无效规则 ID: " + ruleId);
    }
}

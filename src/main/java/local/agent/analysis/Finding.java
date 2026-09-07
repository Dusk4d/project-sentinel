package local.agent.analysis;

public record Finding(String ruleId, Severity severity, String category, String message, String evidence, String action,
                      RuleWaiver waiver) {
    public Finding(String ruleId, Severity severity, String category, String message, String evidence, String action) {
        this(ruleId, severity, category, message, evidence, action, null);
    }
    public Finding {
        if (ruleId == null || !ruleId.matches("[a-z][a-z0-9.-]*"))
            throw new IllegalArgumentException("无效规则 ID: " + ruleId);
    }

    public boolean waived() { return waiver != null; }
    public Finding withWaiver(RuleWaiver value) { return new Finding(ruleId, severity, category, message, evidence, action, value); }
}

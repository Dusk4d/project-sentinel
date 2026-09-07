package local.agent.analysis;

public enum Severity {
    HIGH("高"), MEDIUM("中"), LOW("低"), INFO("信息");
    private final String label;
    Severity(String label) { this.label = label; }
    public String label() { return label; }
}

package local.agent.analysis;

public record Finding(Severity severity, String category, String message, String evidence, String action) { }

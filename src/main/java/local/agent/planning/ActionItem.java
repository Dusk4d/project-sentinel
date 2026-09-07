package local.agent.planning;

import local.agent.analysis.Severity;

public record ActionItem(int rank, String ruleId, Severity severity, String category, String action, String rationale, int potentialScoreGain) { }

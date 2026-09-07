package local.agent.planning;

import local.agent.analysis.Severity;

public record ActionItem(int rank, Severity severity, String category, String action, String rationale, int potentialScoreGain) { }

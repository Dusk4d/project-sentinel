package local.agent.daily;

import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;

import java.util.Set;
import java.util.stream.Collectors;

public record RiskBaseline(String projectRoot, Set<String> highRiskRuleIds) {
    public RiskBaseline {
        if (projectRoot == null || projectRoot.isBlank()) throw new IllegalArgumentException("风险基线缺少项目路径");
        highRiskRuleIds = Set.copyOf(highRiskRuleIds);
    }

    public static RiskBaseline from(ProjectProfile profile) {
        Set<String> ids = profile.findings().stream()
                .filter(finding -> finding.severity() == Severity.HIGH && !finding.waived())
                .map(finding -> finding.ruleId())
                .collect(Collectors.toUnmodifiableSet());
        return new RiskBaseline(profile.root().toAbsolutePath().normalize().toString(), ids);
    }
}

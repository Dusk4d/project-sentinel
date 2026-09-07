package local.agent.planning;

import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ActionPlanner {
    public List<ActionItem> plan(ProjectProfile profile) {
        var actionable = profile.findings().stream()
                .filter(f -> f.severity() != Severity.INFO)
                .filter(f -> !f.waived())
                .sorted(Comparator.comparingInt((local.agent.analysis.Finding f) -> profile.scoreWeights().deduction(f.severity())).reversed()
                        .thenComparing(local.agent.analysis.Finding::category)
                        .thenComparing(local.agent.analysis.Finding::message))
                .toList();
        var result = new ArrayList<ActionItem>();
        for (int i = 0; i < actionable.size(); i++) {
            var f = actionable.get(i);
            result.add(new ActionItem(i + 1, f.ruleId(), f.severity(), f.category(), f.action(), f.message() + "；证据：" + f.evidence(), profile.scoreWeights().deduction(f.severity())));
        }
        return List.copyOf(result);
    }

}

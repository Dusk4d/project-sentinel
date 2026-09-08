package local.agent.daily;

import java.nio.file.Path;
import java.util.Set;

public record DailyRunResult(int score, int minimumScore, Integer previousScore, int maximumScoreDrop,
                             Path report, Path latestHtml, Path latestJson, Path latestPlanJson,
                             Path history, String trend, boolean hadRiskBaseline,
                             Set<String> newHighRiskRuleIds, Path riskBaseline) {
    public DailyRunResult { newHighRiskRuleIds = Set.copyOf(newHighRiskRuleIds); }
    public int scoreDrop() { return previousScore == null ? 0 : Math.max(0, previousScore - score); }
    public boolean scorePassed() { return score >= minimumScore; }
    public boolean scoreRegressionPassed() { return previousScore == null || scoreDrop() <= maximumScoreDrop; }
    public boolean riskRegressionPassed() { return !hadRiskBaseline || newHighRiskRuleIds.isEmpty(); }
    public boolean regressionPassed() { return scoreRegressionPassed() && riskRegressionPassed(); }
    public boolean passed() { return scorePassed() && regressionPassed(); }
}

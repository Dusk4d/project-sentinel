package local.agent.daily;

import java.nio.file.Path;

public record DailyRunResult(int score, int minimumScore, Integer previousScore, int maximumScoreDrop,
                             Path report, Path latestHtml, Path latestJson, Path latestPlanJson,
                             Path history, String trend) {
    public int scoreDrop() { return previousScore == null ? 0 : Math.max(0, previousScore - score); }
    public boolean scorePassed() { return score >= minimumScore; }
    public boolean regressionPassed() { return previousScore == null || scoreDrop() <= maximumScoreDrop; }
    public boolean passed() { return scorePassed() && regressionPassed(); }
}

package local.agent.daily;

import java.nio.file.Path;

public record DailyRunResult(int score, int minimumScore, Path report, Path latestHtml, Path latestJson,
                             Path history, String trend) {
    public boolean passed() { return score >= minimumScore; }
}

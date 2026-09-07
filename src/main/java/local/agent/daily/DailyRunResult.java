package local.agent.daily;

import java.nio.file.Path;

public record DailyRunResult(int score, int minimumScore, Path report, Path history, String trend) {
    public boolean passed() { return score >= minimumScore; }
}

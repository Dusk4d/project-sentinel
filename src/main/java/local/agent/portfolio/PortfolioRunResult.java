package local.agent.portfolio;

import java.nio.file.Path;

public record PortfolioRunResult(int projectCount, int lowestScore, int minimumScore, Path markdown, Path html, Path json) {
    public boolean passed() { return projectCount > 0 && lowestScore >= minimumScore; }
}

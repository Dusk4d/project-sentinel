package local.agent.portfolio;

import local.agent.analysis.PortfolioAnalyzer;
import local.agent.report.AtomicTextStore;
import local.agent.report.PortfolioHtmlWriter;
import local.agent.report.PortfolioJsonWriter;
import local.agent.report.PortfolioReportWriter;

import java.io.IOException;
import java.nio.file.Path;

public final class PortfolioRunService {
    public PortfolioRunResult run(Path workspace, Path stateDirectory, int minimumScore) throws IOException {
        if (minimumScore < 0 || minimumScore > 100) throw new IllegalArgumentException("最低健康分必须在 0 到 100 之间");
        var projects = new PortfolioAnalyzer().analyze(workspace.toRealPath());
        Path state = stateDirectory.toAbsolutePath().normalize();
        var store = new AtomicTextStore();
        Path markdown = store.write(state.resolve("portfolio.md"), new PortfolioReportWriter().render(projects));
        Path html = store.write(state.resolve("portfolio.html"), new PortfolioHtmlWriter().render(projects));
        Path json = store.write(state.resolve("portfolio.json"), new PortfolioJsonWriter().render(projects));
        int lowest = projects.stream().mapToInt(local.agent.analysis.ProjectProfile::healthScore).min().orElse(0);
        return new PortfolioRunResult(projects.size(), lowest, minimumScore, markdown, html, json);
    }
}

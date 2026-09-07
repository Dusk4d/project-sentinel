package local.agent.tools;

import local.agent.Tool;
import local.agent.ToolResult;
import local.agent.WorkspaceGuard;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.report.MarkdownReportWriter;

public final class HealthCheckTool implements Tool {
    private final WorkspaceGuard guard;
    public HealthCheckTool(WorkspaceGuard guard) { this.guard = guard; }
    public String name() { return "health"; }
    public String description() { return "分析项目健康状况并给出证据化建议"; }

    public ToolResult execute(String input) {
        try {
            var profile = new ProjectAnalyzer().analyze(guard.resolve(input));
            return ToolResult.ok(new MarkdownReportWriter().render(profile));
        } catch (Exception e) { return ToolResult.error(e.getMessage()); }
    }
}

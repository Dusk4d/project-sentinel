package local.agent.report;

import local.agent.analysis.ProjectProfile;

import java.time.OffsetDateTime;
import java.util.List;

public final class PortfolioReportWriter {
    public String render(List<ProjectProfile> projects) {
        var out = new StringBuilder("# 项目组合健康看板\n\n生成时间：")
                .append(OffsetDateTime.now()).append("\n\n");
        if (projects.isEmpty()) return out.append("未发现可识别的项目。\n").toString();
        out.append("| 项目 | 技术栈 | 健康分 | 源码 | 测试 | 待办 |\n")
                .append("|---|---|---:|---:|---:|---:|\n");
        for (var p : projects) {
            out.append('|').append(escape(p.name())).append('|').append(escape(p.ecosystem())).append('|')
                    .append(p.healthScore()).append('|').append(p.sourceFileCount()).append('|')
                    .append(p.testFileCount()).append('|').append(p.todoCount()).append("|\n");
        }
        out.append("\n## 优先关注\n\n");
        projects.stream().limit(5).forEach(p -> {
            var first = p.findings().stream().filter(f -> f.severity() != local.agent.analysis.Severity.INFO).findFirst();
            out.append("- **").append(p.name()).append("**（").append(p.healthScore()).append(" 分）：")
                    .append(first.map(f -> f.message() + "；建议：" + f.action()).orElse("暂无显著风险")).append("\n");
        });
        return out.toString();
    }

    private String escape(String text) { return text.replace("|", "\\|"); }
}

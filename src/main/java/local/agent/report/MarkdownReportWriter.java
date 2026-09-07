package local.agent.report;

import local.agent.analysis.ProjectProfile;
import local.agent.planning.ActionPlanner;

import java.time.OffsetDateTime;

public final class MarkdownReportWriter {
    public String render(ProjectProfile p) {
        var out = new StringBuilder();
        out.append("# 项目健康报告：").append(p.name()).append("\n\n")
                .append("生成时间：").append(OffsetDateTime.now()).append("\n\n")
                .append("## 摘要\n\n")
                .append("- 健康分：**").append(p.healthScore()).append("/100**\n")
                .append("- 技术栈：").append(p.ecosystem()).append("\n")
                .append("- 文件：").append(p.fileCount()).append("；源码：").append(p.sourceFileCount())
                .append("；测试：").append(p.testFileCount()).append("\n")
                .append("- TODO/FIXME/HACK：").append(p.todoCount()).append("\n\n")
                .append("## 发现与行动建议\n\n");
        int index = 1;
        for (var f : p.findings()) {
            out.append(index++).append(". **[").append(f.severity().label()).append("] ")
                    .append(f.category()).append("**：").append(f.message()).append("\n")
                    .append("   - 规则：`").append(f.ruleId()).append("`\n")
                    .append("   - 证据：").append(f.evidence()).append("\n")
                    .append("   - 建议：").append(f.action()).append("\n");
        }
        out.append("\n## 优先行动\n\n");
        var actions = new ActionPlanner().plan(p);
        if (actions.isEmpty()) out.append("当前没有需要修复的风险项；保持每日巡检。\n");
        else actions.stream().limit(5).forEach(a -> out.append(a.rank()).append(". ").append(a.action())
                .append("（预计最多恢复 ").append(a.potentialScoreGain()).append(" 分）\n"));
        return out.toString();
    }
}

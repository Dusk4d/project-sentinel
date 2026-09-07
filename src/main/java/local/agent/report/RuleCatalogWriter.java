package local.agent.report;

import local.agent.analysis.RuleCatalog;

public final class RuleCatalogWriter {
    public String render() {
        var out = new StringBuilder("# Workspace Agent 规则目录\n\n")
                .append("稳定规则 ID 可用于 rules.disabled 和 waiver.<ruleId>。\n\n");
        for (var rule : RuleCatalog.DEFINITIONS) {
            out.append("- ").append(rule.id()).append(" [").append(rule.category()).append("] ")
                    .append(rule.trigger()).append('\n');
        }
        return out.toString();
    }
}

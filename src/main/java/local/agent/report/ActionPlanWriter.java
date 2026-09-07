package local.agent.report;

import local.agent.analysis.ProjectProfile;
import local.agent.planning.ActionPlanner;

public final class ActionPlanWriter {
    public String render(ProjectProfile profile) {
        var actions = new ActionPlanner().plan(profile);
        var out = new StringBuilder("# 行动计划：").append(profile.name()).append("\n\n")
                .append("当前健康分：").append(profile.healthScore()).append("/100\n\n");
        if (actions.isEmpty()) return out.append("当前没有需要修复的风险项。保持每日巡检并关注趋势变化。\n").toString();
        out.append("按风险和预期收益排序：\n\n");
        for (var item : actions) {
            out.append(item.rank()).append(". **[").append(item.severity().label()).append("] ")
                    .append(item.action()).append("**\n")
                    .append("   - 规则：`").append(item.ruleId()).append("`\n")
                    .append("   - 原因：").append(item.rationale()).append("\n")
                    .append("   - 单项预计恢复：最多 ").append(item.potentialScoreGain()).append(" 分\n");
        }
        int total = actions.stream().mapToInt(a -> a.potentialScoreGain()).sum();
        out.append("\n全部完成后理论最高可恢复 ").append(Math.min(100 - profile.healthScore(), total))
                .append(" 分；实际结果以重新扫描为准。\n");
        return out.toString();
    }
}

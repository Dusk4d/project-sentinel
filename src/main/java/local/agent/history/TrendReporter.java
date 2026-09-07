package local.agent.history;

import java.util.List;

public final class TrendReporter {
    public String render(List<HealthSnapshot> history) {
        if (history.isEmpty()) return "尚无历史快照。先运行 --snapshot。\n";
        HealthSnapshot latest = history.get(history.size() - 1);
        var out = new StringBuilder("# 健康趋势：").append(latest.project()).append("\n\n")
                .append("- 快照数：").append(history.size()).append("\n")
                .append("- 当前健康分：").append(latest.score()).append("\n");
        if (history.size() == 1) return out.append("- 趋势：需要至少两次快照\n").toString();
        HealthSnapshot first = history.get(0);
        int scoreDelta = latest.score() - first.score();
        int todoDelta = latest.todos() - first.todos();
        out.append("- 健康分变化：").append(signed(scoreDelta)).append("\n")
                .append("- 待办变化：").append(signed(todoDelta)).append("\n")
                .append("- 判断：").append(scoreDelta > 0 ? "整体改善" : scoreDelta < 0 ? "整体退化，需要检查新增风险" : "总体持平").append("\n");
        return out.toString();
    }
    private String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }
}

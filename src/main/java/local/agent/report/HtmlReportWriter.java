package local.agent.report;

import local.agent.analysis.ProjectProfile;
import local.agent.analysis.Severity;
import local.agent.planning.ActionPlanner;

public final class HtmlReportWriter {
    public String render(ProjectProfile p) {
        var out = new StringBuilder("""
                <!doctype html>
                <html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>项目健康报告</title>
                <style>
                :root{color-scheme:light dark;--bg:#f4f6f8;--card:#fff;--text:#17202a;--muted:#64748b;--high:#c62828;--medium:#ef6c00;--low:#1565c0;--info:#546e7a}
                @media(prefers-color-scheme:dark){:root{--bg:#101418;--card:#192027;--text:#edf2f7;--muted:#a0aec0}}
                *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.6 system-ui,sans-serif}.wrap{max-width:1000px;margin:auto;padding:32px 20px}h1{margin-bottom:4px}.muted{color:var(--muted)}.score{font-size:52px;font-weight:800}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin:24px 0}.card,.finding{background:var(--card);border-radius:12px;padding:18px;box-shadow:0 2px 12px #0001}.metric{font-size:26px;font-weight:700}.finding{margin:12px 0;border-left:6px solid var(--info)}.HIGH{border-color:var(--high)}.MEDIUM{border-color:var(--medium)}.LOW{border-color:var(--low)}code{overflow-wrap:anywhere}.action{margin:8px 0;padding:12px;background:var(--card);border-radius:8px}
                </style></head><body><main class="wrap">
                """);
        out.append("<h1>").append(escape(p.name())).append("</h1><div class=\"muted\">")
                .append(escape(p.root().toString())).append(" · ").append(escape(p.ecosystem())).append("</div>")
                .append("<div class=\"score\">").append(p.healthScore()).append("<small>/100</small></div>")
                .append("<section class=\"grid\">")
                .append(metric("文件", p.fileCount())).append(metric("源码", p.sourceFileCount()))
                .append(metric("测试", p.testFileCount())).append(metric("待办", p.todoCount())).append("</section>")
                .append("<h2>发现</h2>");
        for (var f : p.findings()) {
            out.append("<article class=\"finding ").append(f.severity().name()).append("\"><strong>")
                    .append(escape(f.severity().label())).append(" · ").append(escape(f.category())).append("</strong>")
                    .append("<h3>").append(escape(f.message())).append("</h3><code>").append(escape(f.ruleId()))
                    .append("</code><p>证据：").append(escape(f.evidence())).append("</p><p>建议：")
                    .append(escape(f.action())).append("</p></article>");
        }
        out.append("<h2>优先行动</h2>");
        var actions = new ActionPlanner().plan(p);
        if (actions.isEmpty()) out.append("<p class=\"action\">当前没有需要修复的风险项，保持每日巡检。</p>");
        else for (var a : actions) out.append("<div class=\"action\"><strong>").append(a.rank()).append(". ")
                .append(escape(a.action())).append("</strong><br><span class=\"muted\">").append(escape(a.ruleId()))
                .append(" · 预计最多恢复 ").append(a.potentialScoreGain()).append(" 分</span></div>");
        return out.append("</main></body></html>\n").toString();
    }

    private String metric(String label, int value) {
        return "<div class=\"card\"><div class=\"metric\">" + value + "</div><div class=\"muted\">" + label + "</div></div>";
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

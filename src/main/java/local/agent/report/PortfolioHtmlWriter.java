package local.agent.report;

import local.agent.analysis.ProjectProfile;
import java.util.List;

public final class PortfolioHtmlWriter {
    public String render(List<ProjectProfile> projects) {
        var out = new StringBuilder("""
                <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>项目组合健康看板</title><style>
                :root{color-scheme:light dark;--bg:#f4f6f8;--card:#fff;--text:#17202a;--muted:#64748b}@media(prefers-color-scheme:dark){:root{--bg:#101418;--card:#192027;--text:#edf2f7;--muted:#a0aec0}}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.5 system-ui,sans-serif}.wrap{max-width:1050px;margin:auto;padding:32px 20px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:14px}.card{background:var(--card);padding:18px;border-radius:12px;box-shadow:0 2px 12px #0001}.score{font-size:36px;font-weight:800}.muted{color:var(--muted)}.bar{height:8px;background:#d8dee5;border-radius:9px;overflow:hidden}.fill{height:100%;background:#2e7d32}
                </style></head><body><main class="wrap"><h1>项目组合健康看板</h1>
                """);
        out.append("<p class=\"muted\">发现 ").append(projects.size()).append(" 个项目，按健康分从低到高排列。</p><section class=\"grid\">");
        for (var p : projects) {
            String color = p.healthScore() < 60 ? "#c62828" : p.healthScore() < 80 ? "#ef6c00" : "#2e7d32";
            long risks = p.findings().stream().filter(f -> f.severity() != local.agent.analysis.Severity.INFO).count();
            out.append("<article class=\"card\"><h2>").append(HtmlReportWriter.escape(p.name())).append("</h2>")
                    .append("<div class=\"score\">").append(p.healthScore()).append("</div><div class=\"bar\"><div class=\"fill\" style=\"width:")
                    .append(p.healthScore()).append("%;background:").append(color).append("\"></div></div>")
                    .append("<p>").append(HtmlReportWriter.escape(p.ecosystem())).append(" · ").append(risks).append(" 个风险</p>")
                    .append("<div class=\"muted\">").append(HtmlReportWriter.escape(p.root().toString())).append("</div></article>");
        }
        return out.append("</section></main></body></html>\n").toString();
    }
}

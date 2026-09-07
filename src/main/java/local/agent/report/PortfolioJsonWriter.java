package local.agent.report;

import local.agent.analysis.ProjectProfile;
import java.util.List;

public final class PortfolioJsonWriter {
    public String render(List<ProjectProfile> projects) {
        int lowest = projects.stream().mapToInt(ProjectProfile::healthScore).min().orElse(0);
        var out = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"projectCount\": ")
                .append(projects.size()).append(",\n  \"lowestScore\": ").append(lowest)
                .append(",\n  \"projects\": [");
        for (int i = 0; i < projects.size(); i++) {
            var p = projects.get(i);
            if (i > 0) out.append(',');
            out.append("\n    {\"name\": ").append(JsonReportWriter.quote(p.name()))
                    .append(", \"root\": ").append(JsonReportWriter.quote(p.root().toString()))
                    .append(", \"ecosystem\": ").append(JsonReportWriter.quote(p.ecosystem()))
                    .append(", \"healthScore\": ").append(p.healthScore())
                    .append(", \"findings\": ").append(p.findings().stream().filter(f -> f.severity() != local.agent.analysis.Severity.INFO).count())
                    .append('}');
        }
        if (!projects.isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }
}

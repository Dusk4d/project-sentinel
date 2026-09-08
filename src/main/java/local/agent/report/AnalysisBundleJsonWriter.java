package local.agent.report;

import local.agent.analysis.ProjectProfile;

import java.time.Instant;

public final class AnalysisBundleJsonWriter {
    public String render(ProjectProfile profile) {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"generatedAt\": " + JsonReportWriter.quote(Instant.now().toString()) + ",\n"
                + "  \"report\": " + new JsonReportWriter().render(profile).strip() + ",\n"
                + "  \"plan\": " + new ActionPlanJsonWriter().render(profile).strip() + "\n"
                + "}\n";
    }
}

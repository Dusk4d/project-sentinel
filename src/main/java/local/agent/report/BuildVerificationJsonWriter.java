package local.agent.report;

import local.agent.verification.BuildVerification;

public final class BuildVerificationJsonWriter {
    public String render(BuildVerification result) {
        var out = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"status\": ")
                .append(JsonReportWriter.quote(result.status().name()))
                .append(",\n  \"passed\": ").append(result.passed())
                .append(",\n  \"exitCode\": ").append(result.exitCode())
                .append(",\n  \"durationMillis\": ").append(result.duration().toMillis())
                .append(",\n  \"command\": [");
        for (int i = 0; i < result.command().size(); i++) {
            if (i > 0) out.append(", ");
            out.append(JsonReportWriter.quote(result.command().get(i)));
        }
        return out.append("],\n  \"output\": ").append(JsonReportWriter.quote(result.output())).append("\n}\n").toString();
    }
}

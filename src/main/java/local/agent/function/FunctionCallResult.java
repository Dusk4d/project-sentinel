package local.agent.function;

import local.agent.report.JsonReportWriter;

public record FunctionCallResult(int schemaVersion, String callId, String name, boolean success, String output) {
    public String toJson() {
        return "{\n  \"schemaVersion\": " + schemaVersion + ",\n  \"callId\": " + JsonReportWriter.quote(callId)
                + ",\n  \"name\": " + JsonReportWriter.quote(name) + ",\n  \"success\": " + success
                + ",\n  \"output\": " + JsonReportWriter.quote(output) + "\n}\n";
    }
}

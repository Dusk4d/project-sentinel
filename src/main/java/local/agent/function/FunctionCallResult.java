package local.agent.function;

import local.agent.report.JsonReportWriter;

public record FunctionCallResult(int schemaVersion, String callId, String name, boolean success, boolean evidence, String output) {
    public FunctionCallResult(int schemaVersion, String callId, String name, boolean success, String output) {
        this(schemaVersion, callId, name, success, success, output);
    }
    public String toJson() {
        return "{\n  \"schemaVersion\": " + schemaVersion + ",\n  \"callId\": " + JsonReportWriter.quote(callId)
                + ",\n  \"name\": " + JsonReportWriter.quote(name) + ",\n  \"success\": " + success
                + ",\n  \"evidence\": " + evidence
                + ",\n  \"output\": " + JsonReportWriter.quote(output) + "\n}\n";
    }
}

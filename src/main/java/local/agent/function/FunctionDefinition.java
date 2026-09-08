package local.agent.function;

import local.agent.report.JsonReportWriter;

public record FunctionDefinition(String name, String description, String inputDescription) {
    public String toJson() {
        return "{\"type\":\"function\",\"function\":{\"name\":" + JsonReportWriter.quote(name)
                + ",\"description\":" + JsonReportWriter.quote(description)
                + ",\"parameters\":{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\",\"description\":"
                + JsonReportWriter.quote(inputDescription)
                + "}},\"required\":[\"input\"],\"additionalProperties\":false}}}";
    }
}

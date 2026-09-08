package local.agent.rag;

import local.agent.report.JsonReportWriter;

import java.util.List;

public record RagAnswer(int schemaVersion, String question, String answer, List<RagHit> evidence) {
    public String renderText() {
        var out = new StringBuilder("# 本地 RAG 回答（抽取式）\n\n")
                .append(answer).append("\n\n## 检索证据\n");
        if (evidence.isEmpty()) return out.append("\n未找到相关证据。\n").toString();
        for (int i = 0; i < evidence.size(); i++) {
            RagHit hit = evidence.get(i);
            out.append("\n").append(i + 1).append(". `").append(hit.path()).append(':')
                    .append(hit.startLine()).append('-').append(hit.endLine()).append("`，相关度 ")
                    .append(String.format(java.util.Locale.ROOT, "%.3f", hit.score())).append("\n   ")
                    .append(hit.text().replace('\n', ' ').strip()).append("\n");
        }
        return out.toString();
    }

    public String toJson() {
        var out = new StringBuilder("{\n  \"schemaVersion\": ").append(schemaVersion)
                .append(",\n  \"question\": ").append(JsonReportWriter.quote(question))
                .append(",\n  \"answer\": ").append(JsonReportWriter.quote(answer)).append(",\n  \"evidence\": [");
        for (int i = 0; i < evidence.size(); i++) {
            RagHit hit = evidence.get(i);
            if (i > 0) out.append(',');
            out.append("\n    {\"path\":").append(JsonReportWriter.quote(hit.path()))
                    .append(",\"startLine\":").append(hit.startLine()).append(",\"endLine\":")
                    .append(hit.endLine()).append(",\"score\":")
                    .append(String.format(java.util.Locale.ROOT, "%.6f", hit.score()))
                    .append(",\"text\":").append(JsonReportWriter.quote(hit.text())).append('}');
        }
        if (!evidence.isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }
}

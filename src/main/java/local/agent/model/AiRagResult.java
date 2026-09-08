package local.agent.model;

import local.agent.rag.RagAnswer;
import local.agent.report.JsonReportWriter;

public record AiRagResult(String answer, boolean modelUsed, String notice, RagAnswer retrieval) {
    public String renderText() {
        var out = new StringBuilder("# 模型增强 RAG 回答\n\n").append(answer).append("\n");
        if (!notice.isBlank()) out.append("\n> ").append(notice).append("\n");
        out.append("\n## 检索证据\n");
        if (retrieval.evidence().isEmpty()) return out.append("\n未找到相关证据。\n").toString();
        for (int i = 0; i < retrieval.evidence().size(); i++) {
            var hit = retrieval.evidence().get(i);
            out.append("\n").append(i + 1).append(". `").append(hit.path()).append(':')
                    .append(hit.startLine()).append('-').append(hit.endLine()).append("`\n");
        }
        return out.toString();
    }

    public String toJson() {
        var out = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"answer\": ")
                .append(JsonReportWriter.quote(answer)).append(",\n  \"modelUsed\": ").append(modelUsed)
                .append(",\n  \"notice\": ").append(JsonReportWriter.quote(notice)).append(",\n  \"evidence\": [");
        for (int i = 0; i < retrieval.evidence().size(); i++) {
            var hit = retrieval.evidence().get(i);
            if (i > 0) out.append(',');
            out.append("\n    {\"path\":").append(JsonReportWriter.quote(hit.path()))
                    .append(",\"startLine\":").append(hit.startLine()).append(",\"endLine\":")
                    .append(hit.endLine()).append(",\"score\":")
                    .append(String.format(java.util.Locale.ROOT, "%.6f", hit.score()))
                    .append(",\"text\":").append(JsonReportWriter.quote(hit.text())).append('}');
        }
        if (!retrieval.evidence().isEmpty()) out.append('\n').append("  ");
        return out.append("]\n}\n").toString();
    }
}

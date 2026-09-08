package local.agent.model;

import local.agent.rag.RagAnswer;

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
}

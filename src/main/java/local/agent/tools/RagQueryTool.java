package local.agent.tools;

import local.agent.Tool;
import local.agent.ToolResult;
import local.agent.WorkspaceGuard;
import local.agent.rag.LocalRagService;

public final class RagQueryTool implements Tool {
    private final LocalRagService rag;
    public RagQueryTool(WorkspaceGuard guard) { this.rag = new LocalRagService(guard); }
    public String name() { return "rag_query"; }
    public String description() { return "检索本地项目知识并返回带路径和行号的证据化回答"; }
    public ToolResult execute(String input) {
        try {
            var answer = rag.ask(input);
            return answer.evidence().isEmpty() ? ToolResult.noEvidence(answer.renderText()) : ToolResult.ok(answer.renderText());
        }
        catch (IllegalArgumentException | java.io.IOException e) { return ToolResult.error(e.getMessage()); }
    }
}

package local.agent.rag;

public record RagHit(String path, int startLine, int endLine, double score, String text) { }

package local.agent.model;

import java.time.Instant;

public record AgentMemoryEntry(Instant completedAt, String task, String answer, int modelRounds, int toolCalls) { }

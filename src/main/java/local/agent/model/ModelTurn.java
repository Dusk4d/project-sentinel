package local.agent.model;

import java.util.List;

record ModelTurn(String content, List<ModelToolCall> toolCalls, String assistantMessageJson) { }

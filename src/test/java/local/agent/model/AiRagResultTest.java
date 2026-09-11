package local.agent.model;

import local.agent.rag.RagAnswer;
import local.agent.rag.RagHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class AiRagResultTest {
    @Test void serializesEvidenceDirectlyWhenQuestionAndAnswerContainBrackets() {
        var retrieval = new RagAnswer(1, "数组 [0] 怎么用？", "本地 [回答]",
                List.of(new RagHit("src/[demo].java", 2, 3, 1.25, "line \"one\"\nline two")));
        String json = new AiRagResult("模型 [回答]", true, "", retrieval).toJson();
        assertTrue(json.startsWith("{\n"));
        assertTrue(json.contains("\"answer\": \"模型 [回答]\""));
        assertTrue(json.contains("\"evidence\": [\n    {\"path\":\"src/[demo].java\""));
        assertTrue(json.contains("line \\\"one\\\"\\nline two"));
        assertTrue(json.contains("\"indexTruncated\": false"));
        assertTrue(json.endsWith("\"indexTruncated\": false\n}\n"));
    }
}

package local.agent;

import local.agent.report.BuildVerificationJsonWriter;
import local.agent.verification.BuildVerification;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BuildVerificationJsonWriterTest {
    @Test void rendersParseableContractWithEscapedCommandAndOutput() {
        var result = new BuildVerification(BuildVerification.Status.FAILED, List.of("tool", "a\"b"), 7,
                Duration.ofMillis(123), "line1\nline2\\end");
        String json = new BuildVerificationJsonWriter().render(result);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"status\": \"FAILED\""));
        assertTrue(json.contains("\"passed\": false"));
        assertTrue(json.contains("\"exitCode\": 7"));
        assertTrue(json.contains("a\\\"b"));
        assertFalse(json.contains("line1\nline2"));
    }
}

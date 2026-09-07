package local.agent.verification;

import java.time.Duration;
import java.util.List;

public record BuildVerification(Status status, List<String> command, int exitCode, Duration duration, String output) {
    public enum Status { PASSED, FAILED, TIMED_OUT, UNSUPPORTED }
    public boolean passed() { return status == Status.PASSED; }
}

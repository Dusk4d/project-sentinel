package local.agent;

import local.agent.verification.BuildVerification;
import local.agent.verification.BuildVerifier;
import local.agent.verification.ProcessFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BuildVerifierTest {
    @TempDir Path project;

    @Test void reportsPassingAndFailingExitCodes() throws Exception {
        var passed = verifier("pass").verify(project, Duration.ofSeconds(5));
        assertEquals(BuildVerification.Status.PASSED, passed.status());
        assertEquals(0, passed.exitCode());
        assertTrue(passed.output().contains("fixture passed"));

        var failed = verifier("fail").verify(project, Duration.ofSeconds(5));
        assertEquals(BuildVerification.Status.FAILED, failed.status());
        assertEquals(7, failed.exitCode());
        assertTrue(failed.output().contains("fixture failed"));
    }

    @Test void terminatesTimedOutProcess() throws Exception {
        var result = verifier("sleep").verify(project, Duration.ofMillis(150));
        assertEquals(BuildVerification.Status.TIMED_OUT, result.status());
        assertEquals(-1, result.exitCode());
        assertTrue(result.duration().compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test void drainsButBoundsLargeOutput() throws Exception {
        var result = verifier("large").verify(project, Duration.ofSeconds(5));
        assertEquals(BuildVerification.Status.PASSED, result.status());
        assertTrue(result.output().length() < 66_000);
        assertTrue(result.output().endsWith("[输出已截断]"));
    }

    private BuildVerifier verifier(String mode) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
        String classes = Path.of(ProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return new BuildVerifier(ignored -> List.of(executable, "-cp", classes, ProcessFixture.class.getName(), mode));
    }

    private boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
}

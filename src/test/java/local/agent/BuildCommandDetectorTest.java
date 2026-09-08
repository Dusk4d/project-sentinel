package local.agent;

import local.agent.verification.BuildCommandDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class BuildCommandDetectorTest {
    @TempDir Path project;

    @Test void detectsWindowsWrapperAndFallsBackToSystemMaven() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("mvnw.cmd"), "wrapper");
        assertEquals("cmd.exe", new BuildCommandDetector(true).detect(project).get(0));
        assertTrue(new BuildCommandDetector(true).detect(project).contains("mvnw.cmd"));
        Files.delete(project.resolve("mvnw.cmd"));
        assertEquals("mvn.cmd", new BuildCommandDetector(true).detect(project).get(0));
    }

    @Test void detectsUnixWrapperAndFallsBackToSystemMaven() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("mvnw"), "wrapper");
        assertEquals("./mvnw", new BuildCommandDetector(false).detect(project).get(0));
        Files.delete(project.resolve("mvnw"));
        assertEquals("mvn", new BuildCommandDetector(false).detect(project).get(0));
    }

    @Test void returnsEmptyForUnknownProject() { assertTrue(new BuildCommandDetector().detect(project).isEmpty()); }
}

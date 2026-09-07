package local.agent;

import local.agent.verification.BuildCommandDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class BuildCommandDetectorTest {
    @TempDir Path project;

    @Test void prefersWrapperAndDetectsCommonEcosystems() throws Exception {
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("mvnw.cmd"), "wrapper");
        assertTrue(new BuildCommandDetector().detect(project).contains("mvnw.cmd"));
        Files.delete(project.resolve("mvnw.cmd"));
        assertTrue(new BuildCommandDetector().detect(project).stream().anyMatch(s -> s.contains("mvn")));
    }

    @Test void returnsEmptyForUnknownProject() { assertTrue(new BuildCommandDetector().detect(project).isEmpty()); }
}

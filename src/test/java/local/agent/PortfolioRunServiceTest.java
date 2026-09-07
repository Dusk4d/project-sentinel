package local.agent;

import local.agent.portfolio.PortfolioRunService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class PortfolioRunServiceTest {
    @TempDir Path temp;

    @Test void writesThreeStablePortfolioFormatsAndGatesLowestProject() throws Exception {
        createHealthyProject(temp.resolve("healthy"));
        Path weak = temp.resolve("weak");
        Files.createDirectories(weak);
        Files.writeString(weak.resolve("package.json"), "{}");
        var result = new PortfolioRunService().run(temp, temp.resolve("state"), 80);
        assertEquals(2, result.projectCount());
        assertFalse(result.passed());
        assertTrue(Files.readString(result.markdown()).contains("healthy"));
        assertTrue(Files.readString(result.html()).startsWith("<!doctype html>"));
        assertTrue(Files.readString(result.json()).contains("\"projectCount\": 2"));
    }

    private void createHealthyProject(Path project) throws Exception {
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("src/test/java"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("mvnw"), "wrapper");
        Files.writeString(project.resolve("README.md"), "# healthy");
        Files.writeString(project.resolve("LICENSE"), "MIT");
        Files.writeString(project.resolve(".gitignore"), "target/");
        Files.writeString(project.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(project.resolve("src/test/java/AppTest.java"), "class AppTest {}");
    }
}

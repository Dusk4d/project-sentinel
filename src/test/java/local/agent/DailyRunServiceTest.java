package local.agent;

import local.agent.daily.DailyRunService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class DailyRunServiceTest {
    @TempDir Path temp;

    @Test void performsCompleteDailyWorkflow() throws Exception {
        Path project = temp.resolve("project");
        Path state = temp.resolve("state");
        Files.createDirectories(project.resolve("src/main/java"));
        Files.createDirectories(project.resolve("src/test/java"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Files.writeString(project.resolve("README.md"), "# demo");
        Files.writeString(project.resolve("LICENSE"), "MIT");
        Files.writeString(project.resolve(".gitignore"), "target/");
        Files.writeString(project.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(project.resolve("src/test/java/AppTest.java"), "class AppTest {}");

        var service = new DailyRunService();
        var first = service.run(project, state, 90);
        assertTrue(first.passed());
        assertTrue(Files.isRegularFile(first.report()));
        assertTrue(Files.isRegularFile(first.history()));
        assertTrue(first.trend().contains("快照数：1"));

        var second = service.run(project, state, 100);
        assertTrue(second.trend().contains("快照数：2"));
        assertThrows(IllegalArgumentException.class, () -> service.run(project, state, 101));
    }
}

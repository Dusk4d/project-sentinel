package local.agent;

import local.agent.analysis.ProjectAnalyzer;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class ProjectAnalyzerTest {
    @TempDir Path root;
    @Test void detectsEcosystemAndMissingTests() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve("README.md"), "# demo");
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/App.java"), "class App {}");
        var profile = new ProjectAnalyzer().analyze(root);
        assertEquals("Java / Maven", profile.ecosystem());
        assertTrue(profile.hasReadme());
        assertTrue(profile.hasBuildFile());
        assertTrue(profile.healthScore() < 100);
    }

    @Test void flagsSensitiveFileNamesWithoutReadingTheirContents() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve(".env"), "DO_NOT_READ=this-is-not-inspected");
        var profile = new ProjectAnalyzer().analyze(root);
        assertTrue(profile.findings().stream().anyMatch(f -> f.category().equals("安全") && f.severity().name().equals("HIGH")));
    }

    @Test void countsOnlyActionMarkersInSourceComments() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve("README.md"), "TODO is a documented keyword");
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/App.java"), "class App { String text = \"TODO\"; // TODO implement\n}");
        assertEquals(1, new ProjectAnalyzer().analyze(root).todoCount());
    }

    @Test void reportsMissingAndPresentDependencyLock() throws Exception {
        Files.writeString(root.resolve("package.json"), "{}");
        var unlocked = new ProjectAnalyzer().analyze(root);
        assertTrue(unlocked.findings().stream().anyMatch(f -> f.message().contains("版本未锁定")));
        Files.writeString(root.resolve("package-lock.json"), "{}");
        var locked = new ProjectAnalyzer().analyze(root);
        assertFalse(locked.findings().stream().anyMatch(f -> f.message().contains("版本未锁定")));
    }

    @Test void excludesFileSymlinkPointingOutsideProject() throws Exception {
        Path project = Files.createDirectory(root.resolve("project"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path outside = Files.writeString(root.resolve("Outside.java"), "// TODO must-not-be-read\nclass Outside {}");
        try { Files.createSymbolicLink(project.resolve("Linked.java"), outside); }
        catch (Exception e) { assumeTrue(false, "平台不允许创建符号链接: " + e.getMessage()); }
        var profile = new ProjectAnalyzer().analyze(project);
        assertEquals(0, profile.sourceFileCount());
        assertEquals(0, profile.todoCount());
    }
}

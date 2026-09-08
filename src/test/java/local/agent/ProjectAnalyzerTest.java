package local.agent;

import local.agent.analysis.ProjectAnalyzer;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import local.agent.analysis.RuleCatalog;
import local.agent.analysis.ScoreWeights;
import local.agent.config.AnalyzerConfig;
import java.util.Set;
import java.util.Map;

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
        assertTrue(RuleCatalog.KNOWN_IDS.containsAll(profile.findings().stream().map(f -> f.ruleId()).toList()));
    }

    @Test void requiresANonEmptyReadmeAtTheProjectRoot() throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/README.md"), "nested documentation");
        Files.writeString(root.resolve("READMEevil.md"), "misleading filename");
        var nestedOnly = new ProjectAnalyzer().analyze(root);
        assertFalse(nestedOnly.hasReadme());
        assertTrue(nestedOnly.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.DOCS_README)));

        Files.writeString(root.resolve("README.md"), "  \r\n\t");
        var empty = new ProjectAnalyzer().analyze(root);
        assertTrue(empty.hasReadme());
        assertTrue(empty.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.DOCS_README_EMPTY)));

        Files.writeString(root.resolve("README.md"), "# Useful project");
        var documented = new ProjectAnalyzer().analyze(root);
        assertFalse(documented.findings().stream().anyMatch(f -> f.ruleId().startsWith("docs.readme")));
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
        var profile = new ProjectAnalyzer().analyze(root);
        assertEquals(1, profile.todoCount());
        var todo = profile.findings().stream().filter(f -> f.ruleId().equals(RuleCatalog.MAINTENANCE_TODOS)).findFirst().orElseThrow();
        assertTrue(todo.evidence().contains("src/main/java/App.java:1"));
        assertFalse(todo.evidence().contains("TODO implement"));
    }

    @Test void reportsMissingAndPresentDependencyLock() throws Exception {
        Files.writeString(root.resolve("package.json"), "{}");
        var unlocked = new ProjectAnalyzer().analyze(root);
        assertTrue(unlocked.findings().stream().anyMatch(f -> f.message().contains("版本未锁定")));
        Files.writeString(root.resolve("package-lock.json"), "{}");
        var locked = new ProjectAnalyzer().analyze(root);
        assertFalse(locked.findings().stream().anyMatch(f -> f.message().contains("版本未锁定")));
    }

    @Test void reportsMissingCiAndRecognizesGithubWorkflowWithoutReadingIt() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        var missing = new ProjectAnalyzer().analyze(root);
        assertTrue(missing.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.AUTOMATION_CI)));
        Files.createDirectories(root.resolve(".github/workflows"));
        Files.writeString(root.resolve(".github/workflows/ci.yml"), "not parsed as yaml");
        var configured = new ProjectAnalyzer().analyze(root);
        assertFalse(configured.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.AUTOMATION_CI)));
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

    @Test void reportsFileLimitOnlyAfterProvingAnAdditionalFileExists() throws Exception {
        var config = new AnalyzerConfig(Set.of(), Set.of(), 100, 262_144, 20, ScoreWeights.DEFAULT, Map.of());
        for (int i = 0; i < 100; i++) Files.writeString(root.resolve("file-" + i + ".txt"), "");
        var exact = new ProjectAnalyzer().analyze(root, config);
        assertEquals(100, exact.fileCount());
        assertFalse(exact.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.SCAN_FILE_LIMIT)));

        Files.writeString(root.resolve("file-extra.txt"), "");
        var overflow = new ProjectAnalyzer().analyze(root, config);
        assertEquals(100, overflow.fileCount());
        var finding = overflow.findings().stream().filter(f -> f.ruleId().equals(RuleCatalog.SCAN_FILE_LIMIT)).findFirst().orElseThrow();
        assertTrue(finding.evidence().contains("至少存在 101 个"));
    }

    @Test void prunesIgnoredDirectoryBeforeItCanConsumeTheFileBudget() throws Exception {
        Path ignored = Files.createDirectories(root.resolve("node_modules/dependency"));
        for (int i = 0; i < 150; i++) Files.writeString(ignored.resolve("generated-" + i + ".js"), "// TODO ignored");
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        var config = new AnalyzerConfig(Set.of("node_modules"), Set.of(), 100, 262_144, 20, ScoreWeights.DEFAULT, Map.of());

        var profile = new ProjectAnalyzer().analyze(root, config);

        assertEquals(1, profile.fileCount());
        assertEquals(0, profile.todoCount());
        assertFalse(profile.findings().stream().anyMatch(f -> f.ruleId().equals(RuleCatalog.SCAN_FILE_LIMIT)));
    }
}

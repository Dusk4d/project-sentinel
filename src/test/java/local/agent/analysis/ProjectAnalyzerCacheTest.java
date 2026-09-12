package local.agent.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ProjectAnalyzerCacheTest {
    @TempDir Path project;

    @Test void reusesUnchangedTodoScanAndInvalidatesChangedOrDeletedSources() throws Exception {
        Path first = Files.writeString(project.resolve("First.java"), "class First {} // TODO first\n");
        Path second = Files.writeString(project.resolve("Second.java"), "class Second {}\n");
        var analyzer = new ProjectAnalyzer();

        assertEquals(1, analyzer.analyze(project).todoCount());
        assertEquals(2, analyzer.todoFilesRead());
        assertEquals(1, analyzer.analyze(project).todoCount());
        assertEquals(2, analyzer.todoFilesRead(), "未变化源码不得重复读取");

        Files.writeString(second, "class Second {} // TODO second and longer\n");
        assertEquals(2, analyzer.analyze(project).todoCount());
        assertEquals(3, analyzer.todoFilesRead(), "只应重新读取变化源码");

        Files.delete(first);
        assertEquals(1, analyzer.analyze(project).todoCount(), "删除源码不得残留缓存结果");
        assertEquals(3, analyzer.todoFilesRead());
    }

    @Test void keepsIndependentCachesWhenWebStyleProjectSelectionChanges() throws Exception {
        Path first = Files.createDirectories(project.resolve("first"));
        Path second = Files.createDirectories(project.resolve("second"));
        Files.writeString(first.resolve("First.java"), "class First {} // TODO first\n");
        Files.writeString(second.resolve("Second.java"), "class Second {} // TODO second\n");
        var analyzer = new ProjectAnalyzer();

        assertEquals(1, analyzer.analyze(first).todoCount());
        assertEquals(1, analyzer.analyze(second).todoCount());
        assertEquals(2, analyzer.todoFilesRead());

        assertEquals(1, analyzer.analyze(first).todoCount());
        assertEquals(2, analyzer.todoFilesRead(), "切回未变化项目时应复用其独立缓存");
    }

    @Test void reusesAndInvalidatesReadmeAndBuildManifestContentChecks() throws Exception {
        Path readme = Files.writeString(project.resolve("README.md"), "项目说明\n");
        Path pom = Files.writeString(project.resolve("pom.xml"), "<project/>\n");
        var analyzer = new ProjectAnalyzer();

        var initial = analyzer.analyze(project);
        assertEquals(true, initial.hasReadme());
        assertEquals(true, initial.hasBuildFile());
        assertEquals(2, analyzer.meaningfulTextFilesRead());
        analyzer.analyze(project);
        assertEquals(2, analyzer.meaningfulTextFilesRead(), "未变化的 README 与构建清单不得重复读取");

        Files.writeString(readme, "   \n");
        var emptyReadme = analyzer.analyze(project);
        assertEquals(3, analyzer.meaningfulTextFilesRead(), "只应重新读取变化的 README");
        assertEquals("docs.readme-empty", emptyReadme.findings().stream()
                .filter(finding -> finding.ruleId().startsWith("docs.readme"))
                .findFirst().orElseThrow().ruleId());

        Files.delete(pom);
        assertEquals(false, analyzer.analyze(project).hasBuildFile());
        assertEquals(3, analyzer.meaningfulTextFilesRead(), "删除清单不得触发无意义读取或保留旧结果");
    }
}

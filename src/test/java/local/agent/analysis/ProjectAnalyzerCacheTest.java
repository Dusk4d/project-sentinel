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
}

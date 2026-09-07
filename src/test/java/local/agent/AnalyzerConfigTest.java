package local.agent;

import local.agent.analysis.ProjectAnalyzer;
import local.agent.config.AnalyzerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class AnalyzerConfigTest {
    @TempDir Path root;

    @Test void appliesAdditionalIgnoredDirectoriesAndTodoThreshold() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve(AnalyzerConfig.FILE_NAME), "ignore.directories=generated\ntodo.warningThreshold=0\n");
        Files.createDirectories(root.resolve("src/main/java"));
        Files.createDirectories(root.resolve("generated"));
        Files.writeString(root.resolve("src/main/java/App.java"), "// TODO real\nclass App {}");
        Files.writeString(root.resolve("generated/Noise.java"), "// TODO ignored\nclass Noise {}");
        var profile = new ProjectAnalyzer().analyze(root);
        assertEquals(1, profile.todoCount());
        assertTrue(profile.findings().stream().anyMatch(f -> f.message().contains("待办标记较多")));
    }

    @Test void rejectsUnsafeOrOutOfRangeConfiguration() throws Exception {
        Files.writeString(root.resolve(AnalyzerConfig.FILE_NAME), "ignore.directories=../outside\nscan.maxFiles=2\n");
        IOException error = assertThrows(IOException.class, () -> new ProjectAnalyzer().analyze(root));
        assertTrue(error.getMessage().contains("配置文件无效"));
    }

    @Test void disablesOnlyExplicitRuleIds() throws Exception {
        Files.writeString(root.resolve(AnalyzerConfig.FILE_NAME), "rules.disabled=docs.readme,legal.license\n");
        var profile = new ProjectAnalyzer().analyze(root);
        assertFalse(profile.findings().stream().anyMatch(f -> f.ruleId().equals("docs.readme")));
        assertFalse(profile.findings().stream().anyMatch(f -> f.ruleId().equals("legal.license")));
        assertTrue(profile.findings().stream().anyMatch(f -> f.ruleId().equals("build.manifest")));
    }

    @Test void appliesValidatedScoreWeights() throws Exception {
        Files.writeString(root.resolve(AnalyzerConfig.FILE_NAME), "score.high=40\nscore.medium=15\nscore.low=2\n");
        var profile = new ProjectAnalyzer().analyze(root);
        assertEquals(40, profile.scoreWeights().high());
        assertEquals(15, profile.scoreWeights().medium());
        assertEquals(2, profile.scoreWeights().low());
        assertEquals(41, profile.healthScore());
    }

    @Test void rejectsInvertedScoreWeights() throws Exception {
        Files.writeString(root.resolve(AnalyzerConfig.FILE_NAME), "score.high=5\nscore.medium=20\nscore.low=1\n");
        assertThrows(IOException.class, () -> new ProjectAnalyzer().analyze(root));
    }
}

package local.agent;

import local.agent.analysis.PortfolioAnalyzer;
import local.agent.config.AnalyzerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class PortfolioConfigInheritanceTest {
    @TempDir Path workspace;

    @Test void inheritsWorkspaceDefaultsAndAppliesProjectOverrides() throws Exception {
        Files.writeString(workspace.resolve(AnalyzerConfig.FILE_NAME), "score.high=40\nscore.medium=20\nscore.low=10\nrules.disabled=legal.license\n");
        Path inherited = createProject("inherited");
        Path overridden = createProject("overridden");
        Files.writeString(overridden.resolve(AnalyzerConfig.FILE_NAME), "score.high=30\nscore.medium=15\nscore.low=5\nrules.disabled=docs.readme\n");

        var profiles = new PortfolioAnalyzer().analyze(workspace);
        var inheritedProfile = profiles.stream().filter(p -> p.name().equals("inherited")).findFirst().orElseThrow();
        var overriddenProfile = profiles.stream().filter(p -> p.name().equals("overridden")).findFirst().orElseThrow();
        assertEquals(40, inheritedProfile.scoreWeights().high());
        assertEquals(20, inheritedProfile.scoreWeights().medium());
        assertFalse(inheritedProfile.findings().stream().anyMatch(f -> f.ruleId().equals("legal.license")));
        assertEquals(30, overriddenProfile.scoreWeights().high());
        assertEquals(15, overriddenProfile.scoreWeights().medium());
        assertEquals(5, overriddenProfile.scoreWeights().low());
        assertFalse(overriddenProfile.findings().stream().anyMatch(f -> f.ruleId().equals("docs.readme")));
        assertTrue(overriddenProfile.findings().stream().anyMatch(f -> f.ruleId().equals("legal.license")));
    }

    private Path createProject(String name) throws Exception {
        Path project = Files.createDirectory(workspace.resolve(name));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        return project;
    }
}

package local.agent.analysis;

import java.nio.file.Path;
import java.util.List;

public record ProjectProfile(
        Path root,
        String name,
        String ecosystem,
        int fileCount,
        int sourceFileCount,
        int testFileCount,
        int todoCount,
        boolean hasReadme,
        boolean hasBuildFile,
        boolean hasGitIgnore,
        ScoreWeights scoreWeights,
        List<Finding> findings) {

    public ProjectProfile(Path root, String name, String ecosystem, int fileCount, int sourceFileCount,
                          int testFileCount, int todoCount, boolean hasReadme, boolean hasBuildFile,
                          boolean hasGitIgnore, List<Finding> findings) {
        this(root, name, ecosystem, fileCount, sourceFileCount, testFileCount, todoCount, hasReadme,
                hasBuildFile, hasGitIgnore, ScoreWeights.DEFAULT, findings);
    }

    public int healthScore() {
        int deductions = findings.stream().filter(f -> !f.waived()).mapToInt(f -> scoreWeights.deduction(f.severity())).sum();
        return Math.max(0, 100 - deductions);
    }
}

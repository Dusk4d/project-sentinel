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
        List<Finding> findings) {

    public int healthScore() {
        int deductions = findings.stream().mapToInt(f -> switch (f.severity()) {
            case HIGH -> 25;
            case MEDIUM -> 12;
            case LOW -> 5;
            case INFO -> 0;
        }).sum();
        return Math.max(0, 100 - deductions);
    }
}

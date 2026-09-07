package local.agent.history;

import local.agent.analysis.ProjectProfile;
import java.time.Instant;

public record HealthSnapshot(Instant timestamp, String project, int score, int files, int sources, int tests, int todos) {
    public static HealthSnapshot from(ProjectProfile profile) {
        return new HealthSnapshot(Instant.now(), profile.name(), profile.healthScore(), profile.fileCount(),
                profile.sourceFileCount(), profile.testFileCount(), profile.todoCount());
    }
}

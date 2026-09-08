package local.agent.analysis;

import java.nio.file.Path;
import java.util.List;

public record ProjectDiscoveryResult(List<Path> projects, boolean truncated, int maximumProjects) {
    public ProjectDiscoveryResult { projects = List.copyOf(projects); }
}

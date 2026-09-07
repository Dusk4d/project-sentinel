package local.agent.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PortfolioAnalyzer {
    public List<ProjectProfile> analyze(Path workspace) throws IOException {
        var candidates = new ArrayList<Path>();
        if (looksLikeProject(workspace)) candidates.add(workspace);
        try (var children = Files.list(workspace)) {
            children.filter(Files::isDirectory).filter(this::looksLikeProject).limit(100).forEach(candidates::add);
        }
        var profiles = new ArrayList<ProjectProfile>();
        var analyzer = new ProjectAnalyzer();
        for (Path candidate : candidates) profiles.add(analyzer.analyze(candidate));
        profiles.sort(Comparator.comparingInt(ProjectProfile::healthScore));
        return List.copyOf(profiles);
    }

    private boolean looksLikeProject(Path path) {
        return Files.exists(path.resolve("pom.xml")) || Files.exists(path.resolve("build.gradle"))
                || Files.exists(path.resolve("build.gradle.kts")) || Files.exists(path.resolve("package.json"))
                || Files.exists(path.resolve("pyproject.toml")) || Files.exists(path.resolve("Cargo.toml"))
                || Files.exists(path.resolve("go.mod")) || Files.exists(path.resolve(".git"));
    }
}

package local.agent.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PortfolioAnalyzer {
    public List<ProjectProfile> analyze(Path workspace) throws IOException {
        var profiles = new ArrayList<ProjectProfile>();
        var analyzer = new ProjectAnalyzer();
        for (Path candidate : new ProjectDiscovery().discover(workspace)) profiles.add(analyzer.analyze(candidate));
        profiles.sort(Comparator.comparingInt(ProjectProfile::healthScore));
        return List.copyOf(profiles);
    }

}

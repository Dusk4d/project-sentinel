package local.agent.report;

import local.agent.analysis.ProjectProfile;
import java.io.IOException;
import java.nio.file.Path;

public final class JsonReportStore {
    public Path save(ProjectProfile profile, Path outputFile) throws IOException {
        return new AtomicTextStore().write(outputFile, new JsonReportWriter().render(profile));
    }
}

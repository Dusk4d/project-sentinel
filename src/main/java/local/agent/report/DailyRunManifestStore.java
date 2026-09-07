package local.agent.report;

import local.agent.daily.DailyRunResult;
import local.agent.verification.BuildVerification;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

public final class DailyRunManifestStore {
    public Path save(Path stateDirectory, String operation, DailyRunResult daily,
                     BuildVerification build, BuildEvidencePaths evidence) throws IOException {
        String json = new DailyRunManifestWriter().render(operation, Instant.now(), daily, build, evidence);
        return new AtomicTextStore().write(stateDirectory.resolve("latest-run.json"), json);
    }
}

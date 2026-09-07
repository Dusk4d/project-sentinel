package local.agent.report;

import java.nio.file.Path;

public record BuildEvidencePaths(Path latestJson, Path latestLog, Path history, Path archivedLog) { }

package local.agent.config;

import java.nio.file.Path;

public record ConfigInitialization(Path path, boolean created) { }

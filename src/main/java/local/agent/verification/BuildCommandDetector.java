package local.agent.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class BuildCommandDetector {
    private final boolean windows;
    public BuildCommandDetector() { this(System.getProperty("os.name", "").toLowerCase().contains("win")); }
    BuildCommandDetector(boolean windows) { this.windows = windows; }

    public List<String> detect(Path project) {
        if (Files.isRegularFile(project.resolve(windows ? "mvnw.cmd" : "mvnw")))
            return windows ? List.of("cmd.exe", "/d", "/c", "mvnw.cmd", "--batch-mode", "--no-transfer-progress", "test")
                    : List.of("./mvnw", "--batch-mode", "--no-transfer-progress", "test");
        if (Files.isRegularFile(project.resolve("pom.xml"))) return List.of(windows ? "mvn.cmd" : "mvn", "--batch-mode", "--no-transfer-progress", "test");
        if (Files.isRegularFile(project.resolve(windows ? "gradlew.bat" : "gradlew")))
            return windows ? List.of("cmd.exe", "/d", "/c", "gradlew.bat", "test") : List.of("./gradlew", "test");
        if (Files.isRegularFile(project.resolve("package.json"))) return List.of(windows ? "npm.cmd" : "npm", "test");
        if (Files.isRegularFile(project.resolve("Cargo.toml"))) return List.of("cargo", "test");
        if (Files.isRegularFile(project.resolve("go.mod"))) return List.of("go", "test", "./...");
        if (Files.isRegularFile(project.resolve("pyproject.toml"))) return List.of(windows ? "python.exe" : "python3", "-m", "pytest");
        return List.of();
    }
}

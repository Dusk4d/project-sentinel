package local.agent.tools;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

final class ToolFilePolicy {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git", "target", "node_modules", "build", "dist", "coverage", ".idea", ".gradle", "venv", ".venv");

    private ToolFilePolicy() { }

    static boolean ignoredDirectory(Path directory) {
        Path name = directory.getFileName();
        return name != null && IGNORED_DIRECTORIES.contains(name.toString().toLowerCase(Locale.ROOT));
    }

    static boolean ignoredPath(Path root, Path candidate) {
        for (Path part : root.relativize(candidate))
            if (IGNORED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    static boolean sensitive(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals(".env") || name.startsWith(".env.") || name.equals("credentials.json")
                || name.equals("id_rsa") || name.equals("id_ed25519") || name.equals(".npmrc")
                || name.contains("credential") || name.endsWith(".pem") || name.endsWith(".p12")
                || name.endsWith(".pfx") || name.endsWith(".key");
    }
}

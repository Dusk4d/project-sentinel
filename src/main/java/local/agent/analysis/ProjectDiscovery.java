package local.agent.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class ProjectDiscovery {
    public static final int DEFAULT_MAX_DEPTH = 4;
    private static final Set<String> IGNORED = Set.of(".git", "target", "build", ".gradle", ".idea", "node_modules", "agent-state", "reports");

    public List<Path> discover(Path workspace) throws IOException { return discover(workspace, DEFAULT_MAX_DEPTH); }

    public List<Path> discover(Path workspace, int maxDepth) throws IOException {
        if (maxDepth < 1 || maxDepth > 12) throw new IllegalArgumentException("项目发现深度必须在 1 到 12 之间");
        Path root = workspace.toRealPath();
        var found = new ArrayList<Path>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Path relative = root.relativize(directory);
                if (!relative.toString().isEmpty() && isUnderIgnored(relative)) return FileVisitResult.SKIP_SUBTREE;
                Path real = directory.toRealPath();
                if (real.startsWith(root) && looksLikeProject(real)) found.add(real);
                return FileVisitResult.CONTINUE;
            }
        });
        return found.stream().distinct().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private boolean isUnderIgnored(Path relative) {
        for (Path part : relative) if (IGNORED.contains(part.toString())) return true;
        return false;
    }

    private boolean looksLikeProject(Path path) {
        return hasAny(path, "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "pyproject.toml", "Cargo.toml", "go.mod", ".git");
    }

    private boolean hasAny(Path root, String... names) {
        for (String name : names) if (Files.exists(root.resolve(name))) return true;
        return false;
    }
}

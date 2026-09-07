package local.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspaceGuard {
    private final Path root;

    public WorkspaceGuard(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("工作区不存在或无法解析真实路径: " + root, e);
        }
    }

    public Path root() { return root; }

    public Path resolve(String userPath) {
        String value = userPath == null || userPath.isBlank() ? "." : userPath.trim();
        Path requested = Path.of(value);
        if (requested.isAbsolute()) throw new SecurityException("不允许使用绝对路径");
        Path resolved = root.resolve(requested).normalize();
        if (!resolved.startsWith(root)) throw new SecurityException("路径超出工作区");
        if (Files.exists(resolved)) {
            try {
                Path real = resolved.toRealPath();
                if (!real.startsWith(root)) throw new SecurityException("符号链接指向工作区外部");
                return real;
            } catch (IOException e) {
                throw new SecurityException("无法安全解析路径: " + requested, e);
            }
        }
        return resolved;
    }
}

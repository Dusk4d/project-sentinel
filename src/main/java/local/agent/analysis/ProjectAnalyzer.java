package local.agent.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import local.agent.config.AnalyzerConfig;

public final class ProjectAnalyzer {
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(".java", ".kt", ".py", ".js", ".ts", ".go", ".rs", ".c", ".cpp");
    private static final Pattern ACTION_MARKER = Pattern.compile("(?:^|\\s)(?://|#|/\\*|\\*)\\s*(TODO|FIXME|HACK)\\b", Pattern.CASE_INSENSITIVE);

    public ProjectProfile analyze(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("项目目录不存在: " + root.toAbsolutePath().normalize());
        Path normalized = root.toRealPath();
        AnalyzerConfig config = AnalyzerConfig.load(normalized);
        var files = new ArrayList<Path>();
        try (var stream = Files.walk(normalized)) {
            stream.filter(p -> isSafeRegularFile(normalized, p))
                    .filter(p -> !isIgnored(normalized.relativize(p), config))
                    .limit(config.maxFiles())
                    .forEach(files::add);
        }

        boolean readme = files.stream().anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("readme"));
        boolean build = hasAny(normalized, "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "pyproject.toml", "Cargo.toml", "go.mod");
        boolean gitIgnore = Files.isRegularFile(normalized.resolve(".gitignore"));
        boolean license = files.stream().anyMatch(p -> p.getParent().equals(normalized)
                && p.getFileName().toString().toLowerCase(Locale.ROOT).matches("license([._-].*)?|copying([._-].*)?"));
        int sourceCount = (int) files.stream().filter(this::isSource).count();
        int testCount = (int) files.stream().filter(this::isTest).count();
        int todos = countTodos(files, config);
        String ecosystem = detectEcosystem(normalized);

        List<Finding> findings = new ArrayList<>();
        if (!readme) findings.add(new Finding(Severity.MEDIUM, "文档", "缺少 README，项目目标和运行方式不可发现", "项目根目录未发现 README*", "补充目标、安装、运行和测试说明"));
        if (!build) findings.add(new Finding(Severity.HIGH, "可复现性", "缺少可识别的构建清单", "未发现常见构建文件", "添加与技术栈匹配的构建配置"));
        if (!gitIgnore) findings.add(new Finding(Severity.LOW, "版本控制", "缺少 .gitignore", "项目根目录未发现 .gitignore", "排除构建产物、IDE 文件和本地机密"));
        if (!license) findings.add(new Finding(Severity.LOW, "合规", "缺少明确的软件许可证", "项目根目录未发现 LICENSE 或 COPYING", "若计划分享或开源，选择并添加合适许可证"));
        addReproducibilityFinding(normalized, ecosystem, findings);
        var sensitive = files.stream().filter(this::hasSensitiveName).map(normalized::relativize).limit(5).toList();
        if (!sensitive.isEmpty()) findings.add(new Finding(Severity.HIGH, "安全", "发现可能包含密钥或本地配置的敏感文件名",
                "仅检查文件名，未读取内容：" + sensitive, "确认文件未被提交，并通过示例配置和环境变量替代真实凭据"));
        if (sourceCount > 0 && testCount == 0) findings.add(new Finding(Severity.HIGH, "测试", "存在源码但没有识别到测试文件", "源码文件 " + sourceCount + " 个，测试文件 0 个", "为核心行为增加自动化测试"));
        else if (sourceCount > 0 && testCount * 5 < sourceCount) findings.add(new Finding(Severity.MEDIUM, "测试", "测试文件相对源码偏少", "源码 " + sourceCount + " 个，测试 " + testCount + " 个", "优先覆盖高风险核心路径"));
        if (todos > config.todoWarningThreshold()) findings.add(new Finding(Severity.MEDIUM, "维护", "待办标记较多，可能存在积压", "发现 TODO/FIXME/HACK 共 " + todos + " 处，阈值 " + config.todoWarningThreshold(), "分类并为高价值待办设定负责人和完成条件"));
        else findings.add(new Finding(Severity.INFO, "维护", "待办标记数量可控", "发现 TODO/FIXME/HACK 共 " + todos + " 处", "持续在每日报告中观察趋势"));
        if (files.size() >= config.maxFiles()) findings.add(new Finding(Severity.MEDIUM, "规模", "扫描达到 " + config.maxFiles() + " 文件上限", "分析结果可能不完整", "配置更精确的忽略目录或拆分项目"));

        return new ProjectProfile(normalized, normalized.getFileName().toString(), ecosystem, files.size(), sourceCount,
                testCount, todos, readme, build, gitIgnore, List.copyOf(findings));
    }

    private boolean isIgnored(Path relative, AnalyzerConfig config) {
        for (Path part : relative) if (config.ignoredDirectories().contains(part.toString())) return true;
        return false;
    }

    private boolean isSafeRegularFile(Path root, Path candidate) {
        if (!Files.isRegularFile(candidate)) return false;
        try { return candidate.toRealPath().startsWith(root); }
        catch (IOException e) { return false; }
    }

    private boolean isSource(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith) && !isTest(path);
    }

    private boolean hasSensitiveName(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.equals(".env") || name.startsWith(".env.") || name.equals("id_rsa") || name.equals("id_ed25519")
                || name.endsWith(".pem") || name.endsWith(".p12") || name.endsWith(".pfx") || name.equals("credentials.json");
    }

    private boolean isTest(Path path) {
        String value = path.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return value.contains("/test/") || value.contains("/tests/") || name.contains("test") || name.contains("spec");
    }

    private int countTodos(List<Path> files, AnalyzerConfig config) {
        int count = 0;
        for (Path file : files) {
            try {
                if (!isSource(file) || Files.size(file) > config.maxTextBytes()) continue;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (ACTION_MARKER.matcher(line).find()) count++;
                }
            } catch (IOException ignored) { }
        }
        return count;
    }

    private String detectEcosystem(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) return "Java / Maven";
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) return "Java/Kotlin / Gradle";
        if (Files.exists(root.resolve("package.json"))) return "Node.js";
        if (Files.exists(root.resolve("pyproject.toml"))) return "Python";
        if (Files.exists(root.resolve("Cargo.toml"))) return "Rust";
        if (Files.exists(root.resolve("go.mod"))) return "Go";
        return "未知";
    }

    private void addReproducibilityFinding(Path root, String ecosystem, List<Finding> findings) {
        boolean reproducible = switch (ecosystem) {
            case "Java / Maven" -> Files.isRegularFile(root.resolve("mvnw")) || Files.isRegularFile(root.resolve("mvnw.cmd"));
            case "Java/Kotlin / Gradle" -> Files.isRegularFile(root.resolve("gradlew")) || Files.isRegularFile(root.resolve("gradlew.bat"));
            case "Node.js" -> hasAny(root, "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "bun.lock", "bun.lockb");
            case "Python" -> hasAny(root, "uv.lock", "poetry.lock", "Pipfile.lock");
            case "Rust" -> Files.isRegularFile(root.resolve("Cargo.lock"));
            case "Go" -> Files.isRegularFile(root.resolve("go.sum"));
            default -> true;
        };
        if (!reproducible) findings.add(new Finding(Severity.LOW, "可复现性", "依赖或构建工具版本未锁定",
                "技术栈为 " + ecosystem + "，未发现对应 wrapper 或锁文件", "生成并提交该技术栈的 wrapper 或依赖锁文件"));
    }

    private boolean hasAny(Path root, String... names) {
        for (String name : names) if (Files.isRegularFile(root.resolve(name))) return true;
        return false;
    }
}

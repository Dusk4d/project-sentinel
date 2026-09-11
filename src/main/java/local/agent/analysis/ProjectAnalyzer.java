package local.agent.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import local.agent.config.AnalyzerConfig;

public final class ProjectAnalyzer {
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".scala", ".groovy", ".clj",
            ".py", ".rb", ".php", ".js", ".jsx", ".ts", ".tsx", ".vue", ".svelte",
            ".go", ".rs", ".c", ".h", ".cpp", ".hpp", ".m", ".mm",
            ".cs", ".fs", ".vb", ".swift", ".dart", ".lua", ".r", ".sh", ".ps1");
    private static final List<String> BUILD_MANIFEST_NAMES = List.of("pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "pyproject.toml", "Cargo.toml", "go.mod");
    private static final Pattern ACTION_MARKER = Pattern.compile("(?:^|\\s)(?://|#|/\\*|\\*)\\s*(TODO|FIXME|HACK)\\b", Pattern.CASE_INSENSITIVE);
    private final Map<Path, CachedTodoScan> todoCache = new HashMap<>();
    private long todoFilesRead;

    public ProjectProfile analyze(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("项目目录不存在: " + root.toAbsolutePath().normalize());
        Path normalized = root.toRealPath();
        return analyze(normalized, AnalyzerConfig.load(normalized));
    }

    public ProjectProfile analyze(Path root, AnalyzerConfig config) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("项目目录不存在: " + root.toAbsolutePath().normalize());
        Path normalized = root.toRealPath();
        var files = scanFiles(normalized, config);
        boolean truncated = files.size() > config.maxFiles();
        if (truncated) files.remove(files.size() - 1);

        var readmes = files.stream().filter(p -> p.getParent().equals(normalized))
                .filter(this::isReadme).toList();
        boolean readme = !readmes.isEmpty();
        boolean readmeHasContent = readmes.stream().anyMatch(path -> hasMeaningfulText(path, config.maxTextBytes()));
        var buildManifests = BUILD_MANIFEST_NAMES.stream().map(normalized::resolve)
                .filter(path -> isSafeRegularFile(normalized, path)).toList();
        boolean buildManifestPresent = !buildManifests.isEmpty();
        boolean build = buildManifests.stream().anyMatch(path -> hasMeaningfulText(path, config.maxTextBytes()));
        boolean gitIgnore = Files.isRegularFile(normalized.resolve(".gitignore"));
        boolean license = files.stream().anyMatch(p -> p.getParent().equals(normalized)
                && p.getFileName().toString().toLowerCase(Locale.ROOT).matches("license([._-].*)?|copying([._-].*)?"));
        int sourceCount = (int) files.stream().filter(this::isSource).count();
        int testCount = (int) files.stream().filter(this::isTest).count();
        TodoScan todoScan = scanTodos(normalized, files, config);
        int todos = todoScan.count();
        String ecosystem = detectEcosystem(normalized);

        List<Finding> findings = new ArrayList<>();
        if (!readme) add(config, findings, new Finding(RuleCatalog.DOCS_README, Severity.MEDIUM, "文档", "缺少 README，项目目标和运行方式不可发现", "项目根目录未发现 README*", "补充目标、安装、运行和测试说明"));
        else if (!readmeHasContent) add(config, findings, new Finding(RuleCatalog.DOCS_README_EMPTY, Severity.MEDIUM, "文档",
                "README 没有有效内容，仍无法了解项目", "项目根目录 README 文件均为空或只包含空白字符：" + readmes.stream().map(Path::getFileName).toList(),
                "补充项目背景、安装要求、启动命令和测试方法"));
        if (!buildManifestPresent) add(config, findings, new Finding(RuleCatalog.BUILD_MANIFEST, Severity.HIGH, "可复现性", "缺少可识别的构建清单", "未发现常见构建文件", "添加与技术栈匹配的构建配置"));
        else if (!build) add(config, findings, new Finding(RuleCatalog.BUILD_MANIFEST_EMPTY, Severity.HIGH, "可复现性",
                "构建清单没有有效内容，项目无法据此构建", "项目根目录构建清单均为空或只包含空白字符：" + buildManifests.stream().map(Path::getFileName).toList(),
                "写入有效构建配置，并通过对应构建工具执行编译和测试"));
        if (!gitIgnore) add(config, findings, new Finding(RuleCatalog.VCS_GITIGNORE, Severity.LOW, "版本控制", "缺少 .gitignore", "项目根目录未发现 .gitignore", "排除构建产物、IDE 文件和本地机密"));
        if (!license) add(config, findings, new Finding(RuleCatalog.LEGAL_LICENSE, Severity.LOW, "合规", "缺少明确的软件许可证", "项目根目录未发现 LICENSE 或 COPYING", "若计划分享或开源，选择并添加合适许可证"));
        if (build && !hasCi(normalized)) add(config, findings, new Finding(RuleCatalog.AUTOMATION_CI, Severity.LOW, "自动化",
                "项目可构建但未发现 CI 配置", "未发现 GitHub Actions、GitLab CI、Azure Pipelines、CircleCI、Jenkins、Buildkite 或 Bitbucket Pipelines 配置",
                "添加至少执行编译和测试的 CI 流水线"));
        if (build) addReproducibilityFinding(normalized, ecosystem, findings, config);
        var sensitive = files.stream().filter(this::hasSensitiveName).map(normalized::relativize).limit(5).toList();
        if (!sensitive.isEmpty()) add(config, findings, new Finding(RuleCatalog.SECURITY_SENSITIVE_FILE, Severity.HIGH, "安全", "发现可能包含密钥或本地配置的敏感文件名",
                "仅检查文件名，未读取内容：" + sensitive, "确认文件未被提交，并通过示例配置和环境变量替代真实凭据"));
        if (sourceCount > 0 && testCount == 0) add(config, findings, new Finding(RuleCatalog.TESTS_MISSING, Severity.HIGH, "测试", "存在源码但没有识别到测试文件", "源码文件 " + sourceCount + " 个，测试文件 0 个", "为核心行为增加自动化测试"));
        else if (sourceCount > 0 && testCount * 5 < sourceCount) add(config, findings, new Finding(RuleCatalog.TESTS_RATIO, Severity.MEDIUM, "测试", "测试文件相对源码偏少", "源码 " + sourceCount + " 个，测试 " + testCount + " 个", "优先覆盖高风险核心路径"));
        String todoEvidence = "发现 TODO/FIXME/HACK 共 " + todos + " 处" + evidenceLocations(todoScan.locations());
        if (todos > config.todoWarningThreshold()) add(config, findings, new Finding(RuleCatalog.MAINTENANCE_TODOS, Severity.MEDIUM, "维护", "待办标记较多，可能存在积压", todoEvidence + "，阈值 " + config.todoWarningThreshold(), "分类并为高价值待办设定负责人和完成条件"));
        else add(config, findings, new Finding(RuleCatalog.MAINTENANCE_TODOS, Severity.INFO, "维护", "待办标记数量可控", todoEvidence, "持续在每日报告中观察趋势"));
        if (truncated) add(config, findings, new Finding(RuleCatalog.SCAN_FILE_LIMIT, Severity.MEDIUM, "规模", "扫描超过 " + config.maxFiles() + " 文件上限", "已确认至少存在 " + ((long) config.maxFiles() + 1) + " 个符合条件的文件，结果只包含前 " + config.maxFiles() + " 个", "配置更精确的忽略目录或拆分项目"));

        return new ProjectProfile(normalized, normalized.getFileName().toString(), ecosystem, files.size(), sourceCount,
                testCount, todos, readme, build, gitIgnore, config.scoreWeights(), List.copyOf(findings));
    }

    private boolean isIgnored(Path relative, AnalyzerConfig config) {
        for (Path part : relative) if (config.ignoredDirectories().contains(part.toString())) return true;
        return false;
    }

    private ArrayList<Path> scanFiles(Path root, AnalyzerConfig config) throws IOException {
        var files = new ArrayList<Path>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root) && isIgnored(root.relativize(directory), config)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                try {
                    return directory.toRealPath().startsWith(root) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                } catch (IOException e) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (isSafeRegularFile(root, file)) files.add(file);
                return files.size() > config.maxFiles() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFileFailed(Path file, IOException exception) {
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private boolean isSafeRegularFile(Path root, Path candidate) {
        if (!Files.isRegularFile(candidate)) return false;
        try { return candidate.toRealPath().startsWith(root); }
        catch (IOException e) { return false; }
    }

    private boolean hasMeaningfulText(Path file, long maxBytes) {
        try {
            if (Files.size(file) == 0) return false;
            if (Files.size(file) > maxBytes) return true;
            return !Files.readString(file, StandardCharsets.UTF_8).isBlank();
        } catch (IOException unreadable) {
            return true;
        }
    }

    private boolean isReadme(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).matches("readme([._-].*)?");
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

    private synchronized TodoScan scanTodos(Path root, List<Path> files, AnalyzerConfig config) {
        int count = 0;
        var locations = new ArrayList<String>();
        var seen = new HashSet<Path>();
        for (Path file : files) {
            try {
                if (!isSource(file) || Files.size(file) > config.maxTextBytes()) continue;
                Path real = file.toRealPath();
                seen.add(real);
                BasicFileAttributes attributes = Files.readAttributes(real, BasicFileAttributes.class);
                FileStamp stamp = new FileStamp(attributes.size(), attributes.lastModifiedTime(), config.maxTextBytes());
                CachedTodoScan cached = todoCache.get(real);
                if (cached == null || !cached.stamp().equals(stamp)) {
                    var lineNumbers = new ArrayList<Integer>();
                    List<String> lines = Files.readAllLines(real, StandardCharsets.UTF_8);
                    for (int index = 0; index < lines.size(); index++)
                        if (ACTION_MARKER.matcher(lines.get(index)).find()) lineNumbers.add(index + 1);
                    cached = new CachedTodoScan(stamp, List.copyOf(lineNumbers));
                    todoCache.put(real, cached);
                    todoFilesRead++;
                }
                count += cached.lineNumbers().size();
                String relative = root.relativize(real).toString().replace('\\', '/');
                for (int line : cached.lineNumbers())
                    if (locations.size() < 10) locations.add(relative + ":" + line);
            } catch (IOException ignored) { }
        }
        todoCache.keySet().removeIf(path -> path.startsWith(root) && !seen.contains(path));
        return new TodoScan(count, List.copyOf(locations));
    }

    long todoFilesRead() { return todoFilesRead; }

    private String evidenceLocations(List<String> locations) {
        return locations.isEmpty() ? "" : "，位置示例 " + locations;
    }

    private record TodoScan(int count, List<String> locations) { }
    private record FileStamp(long size, FileTime modified, long maxTextBytes) { }
    private record CachedTodoScan(FileStamp stamp, List<Integer> lineNumbers) { }

    private String detectEcosystem(Path root) {
        if (Files.exists(root.resolve("pom.xml"))) return "Java / Maven";
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) return "Java/Kotlin / Gradle";
        if (Files.exists(root.resolve("package.json"))) return "Node.js";
        if (Files.exists(root.resolve("pyproject.toml"))) return "Python";
        if (Files.exists(root.resolve("Cargo.toml"))) return "Rust";
        if (Files.exists(root.resolve("go.mod"))) return "Go";
        return "未知";
    }

    private void addReproducibilityFinding(Path root, String ecosystem, List<Finding> findings, AnalyzerConfig config) {
        boolean reproducible = switch (ecosystem) {
            case "Java / Maven" -> Files.isRegularFile(root.resolve("mvnw")) || Files.isRegularFile(root.resolve("mvnw.cmd"));
            case "Java/Kotlin / Gradle" -> Files.isRegularFile(root.resolve("gradlew")) || Files.isRegularFile(root.resolve("gradlew.bat"));
            case "Node.js" -> hasAny(root, "package-lock.json", "pnpm-lock.yaml", "yarn.lock", "bun.lock", "bun.lockb");
            case "Python" -> hasAny(root, "uv.lock", "poetry.lock", "Pipfile.lock");
            case "Rust" -> Files.isRegularFile(root.resolve("Cargo.lock"));
            case "Go" -> Files.isRegularFile(root.resolve("go.sum"));
            default -> true;
        };
        if (!reproducible) add(config, findings, new Finding(RuleCatalog.BUILD_LOCK, Severity.LOW, "可复现性", "依赖或构建工具版本未锁定",
                "技术栈为 " + ecosystem + "，未发现对应 wrapper 或锁文件", "生成并提交该技术栈的 wrapper 或依赖锁文件"));
    }

    private boolean hasCi(Path root) {
        if (hasAny(root, ".gitlab-ci.yml", "azure-pipelines.yml", "Jenkinsfile",
                ".circleci/config.yml", ".buildkite/pipeline.yml", "bitbucket-pipelines.yml", ".woodpecker.yml")) return true;
        Path workflows = root.resolve(".github/workflows");
        try {
            if (!Files.isDirectory(workflows)) return false;
            Path real = workflows.toRealPath();
            if (!real.startsWith(root)) return false;
            try (var files = Files.list(real)) {
                return files.filter(path -> isSafeRegularFile(root, path)).map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                        .anyMatch(name -> name.endsWith(".yml") || name.endsWith(".yaml"));
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private void add(AnalyzerConfig config, List<Finding> findings, Finding finding) {
        if (!config.enabled(finding.ruleId())) return;
        var waiver = config.activeWaiver(finding.ruleId());
        findings.add(waiver == null ? finding : finding.withWaiver(waiver));
    }

    private boolean hasAny(Path root, String... names) {
        for (String name : names) if (Files.isRegularFile(root.resolve(name))) return true;
        return false;
    }
}

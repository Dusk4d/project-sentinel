package local.agent.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import local.agent.analysis.ScoreWeights;
import local.agent.analysis.RuleWaiver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.LinkedHashMap;

public record AnalyzerConfig(Set<String> ignoredDirectories, Set<String> disabledRules, int maxFiles, long maxTextBytes,
                             int todoWarningThreshold, ScoreWeights scoreWeights, Map<String, RuleWaiver> waivers) {
    public static final String FILE_NAME = ".workspace-agent.properties";
    private static final Set<String> DEFAULT_IGNORED = Set.of(".git", "target", "build", ".idea", ".gradle", "node_modules", "agent-state");

    public AnalyzerConfig {
        ignoredDirectories = Set.copyOf(ignoredDirectories);
        disabledRules = Set.copyOf(disabledRules);
        waivers = Map.copyOf(waivers);
        if (maxFiles < 100 || maxFiles > 1_000_000) throw new IllegalArgumentException("scan.maxFiles 必须在 100 到 1000000 之间");
        if (maxTextBytes < 1_024 || maxTextBytes > 10L * 1024 * 1024) throw new IllegalArgumentException("scan.maxTextBytes 必须在 1024 到 10485760 之间");
        if (todoWarningThreshold < 0 || todoWarningThreshold > 100_000) throw new IllegalArgumentException("todo.warningThreshold 必须在 0 到 100000 之间");
        if (ignoredDirectories.stream().anyMatch(s -> s.isBlank() || s.contains("/") || s.contains("\\") || s.equals("..")))
            throw new IllegalArgumentException("ignore.directories 只能包含目录名，不能包含路径");
        if (disabledRules.stream().anyMatch(s -> !s.matches("[a-z][a-z0-9.-]*")))
            throw new IllegalArgumentException("rules.disabled 包含无效规则 ID");
    }

    public static AnalyzerConfig defaults() { return new AnalyzerConfig(DEFAULT_IGNORED, Set.of(), 10_000, 256 * 1024L, 20, ScoreWeights.DEFAULT, Map.of()); }

    public static AnalyzerConfig load(Path projectRoot) throws IOException {
        return loadScopes(projectRoot);
    }

    public static AnalyzerConfig loadScopes(Path... roots) throws IOException {
        var properties = new Properties();
        var loadedFiles = new java.util.ArrayList<Path>();
        for (Path root : roots) {
            Path file = root.resolve(FILE_NAME);
            if (!Files.isRegularFile(file) || loadedFiles.contains(file.toAbsolutePath().normalize())) continue;
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
            loadedFiles.add(file.toAbsolutePath().normalize());
        }
        if (loadedFiles.isEmpty()) return defaults();
        try {
            var ignored = new LinkedHashSet<>(DEFAULT_IGNORED);
            String extra = properties.getProperty("ignore.directories", "");
            Arrays.stream(extra.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(ignored::add);
            var disabled = commaSet(properties.getProperty("rules.disabled", ""));
            return new AnalyzerConfig(ignored, disabled,
                    integer(properties, "scan.maxFiles", 10_000),
                    integer(properties, "scan.maxTextBytes", 256 * 1024),
                    integer(properties, "todo.warningThreshold", 20),
                    new ScoreWeights(integer(properties, "score.high", 25),
                            integer(properties, "score.medium", 12), integer(properties, "score.low", 5)),
                    parseWaivers(properties));
        } catch (IllegalArgumentException e) {
            throw new IOException("配置文件无效 " + loadedFiles + ": " + e.getMessage(), e);
        }
    }

    public boolean enabled(String ruleId) { return !disabledRules.contains(ruleId); }
    public RuleWaiver activeWaiver(String ruleId) {
        RuleWaiver waiver = waivers.get(ruleId);
        return waiver != null && waiver.activeOn(LocalDate.now()) ? waiver : null;
    }

    private static Set<String> commaSet(String value) {
        var result = new LinkedHashSet<String>();
        Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(result::add);
        return result;
    }

    private static Map<String, RuleWaiver> parseWaivers(Properties properties) {
        var result = new LinkedHashMap<String, RuleWaiver>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("waiver.")) continue;
            String ruleId = key.substring("waiver.".length());
            String[] parts = properties.getProperty(key, "").split("\\|", 3);
            if (parts.length != 3) throw new IllegalArgumentException(key + " 必须使用 到期日|负责人|原因 格式");
            try { result.put(ruleId, new RuleWaiver(ruleId, LocalDate.parse(parts[0].trim()), parts[1].trim(), parts[2].trim())); }
            catch (DateTimeParseException e) { throw new IllegalArgumentException(key + " 到期日必须是 YYYY-MM-DD"); }
        }
        return result;
    }

    private static int integer(Properties p, String name, int fallback) {
        String value = p.getProperty(name);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + " 必须是整数"); }
    }
}

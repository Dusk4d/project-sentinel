package local.agent.daily;

import local.agent.analysis.RuleCatalog;
import local.agent.report.AtomicTextStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class RiskBaselineStore {
    public static final String FILE_NAME = "latest-risk-baseline.properties";
    private static final Set<String> KEYS = Set.of("schemaVersion", "projectRootBase64", "highRiskRuleIds");

    public Optional<RiskBaseline> read(Path file) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        if (!Files.exists(target)) return Optional.empty();
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) { properties.load(reader); }
        var unknown = new LinkedHashSet<>(properties.stringPropertyNames());
        unknown.removeAll(KEYS);
        if (!unknown.isEmpty() || !properties.stringPropertyNames().containsAll(KEYS))
            throw new IOException("风险基线字段无效: " + target);
        if (!"1".equals(properties.getProperty("schemaVersion")))
            throw new IOException("不支持的风险基线版本: " + properties.getProperty("schemaVersion"));
        try {
            String root = new String(Base64.getUrlDecoder().decode(properties.getProperty("projectRootBase64")), StandardCharsets.UTF_8);
            Set<String> ids = properties.getProperty("highRiskRuleIds").isBlank() ? Set.of()
                    : Set.of(properties.getProperty("highRiskRuleIds").split(",", -1));
            if (!RuleCatalog.KNOWN_IDS.containsAll(ids)) throw new IOException("风险基线包含未知规则 ID");
            return Optional.of(new RiskBaseline(root, ids));
        } catch (IllegalArgumentException e) {
            throw new IOException("风险基线内容无效: " + target, e);
        }
    }

    public Path save(Path file, RiskBaseline baseline) throws IOException {
        String encodedRoot = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(baseline.projectRoot().getBytes(StandardCharsets.UTF_8));
        String ids = baseline.highRiskRuleIds().stream().sorted().collect(Collectors.joining(","));
        String content = "schemaVersion=1\nprojectRootBase64=" + encodedRoot + "\nhighRiskRuleIds=" + ids + "\n";
        return new AtomicTextStore().write(file, content);
    }
}

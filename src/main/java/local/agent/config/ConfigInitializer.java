package local.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ConfigInitializer {
    public static final String TEMPLATE = """
            # Project Sentinel 项目级配置。未列出的字段继续使用内置默认值。
            # 额外忽略的目录名（逗号分隔，不能写路径）
            ignore.directories=generated,coverage,reports
            # 扫描资源上限
            scan.maxFiles=10000
            scan.maxTextBytes=262144
            todo.warningThreshold=20
            # 稳定规则 ID，请仅在明确接受风险时禁用
            rules.disabled=
            # 风险扣分权重，要求 high >= medium >= low
            score.high=25
            score.medium=12
            score.low=5
            # 有期限豁免示例（取消下一行注释后修改）
            # waiver.legal.license=2026-12-31|owner|等待确认许可证
            """;

    public ConfigInitialization initialize(Path project) throws IOException {
        Path root = project.toRealPath();
        if (!Files.isDirectory(root)) throw new IOException("项目不是目录: " + root);
        Path destination = root.resolve(AnalyzerConfig.FILE_NAME);
        if (Files.exists(destination)) return new ConfigInitialization(destination, false);
        try {
            Files.writeString(destination, TEMPLATE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new ConfigInitialization(destination, true);
        } catch (java.nio.file.FileAlreadyExistsException race) {
            return new ConfigInitialization(destination, false);
        }
    }
}

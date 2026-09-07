package local.agent.report;

import local.agent.config.AnalyzerConfig;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Collectors;

public final class ConfigSummaryWriter {
    public String render(Path root, AnalyzerConfig config, boolean configured) {
        String ignored = config.ignoredDirectories().stream().sorted().collect(Collectors.joining(", "));
        String disabled = config.disabledRules().stream().sorted().collect(Collectors.joining(", "));
        var out = new StringBuilder("配置有效\n")
                .append("项目: ").append(root).append('\n')
                .append("来源: ").append(configured ? root.resolve(AnalyzerConfig.FILE_NAME) : "内置默认值").append('\n')
                .append("忽略目录: ").append(ignored).append('\n')
                .append("最大文件数: ").append(config.maxFiles()).append('\n')
                .append("最大文本字节: ").append(config.maxTextBytes()).append('\n')
                .append("待办警告阈值: ").append(config.todoWarningThreshold()).append('\n')
                .append("扣分权重: high=").append(config.scoreWeights().high())
                .append(", medium=").append(config.scoreWeights().medium())
                .append(", low=").append(config.scoreWeights().low()).append('\n')
                .append("禁用规则: ").append(disabled.isEmpty() ? "无" : disabled).append('\n')
                .append("豁免数: ").append(config.waivers().size()).append('\n');
        config.waivers().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                out.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue().activeOn(LocalDate.now()) ? "ACTIVE" : "EXPIRED")
                        .append("，到期 ").append(entry.getValue().expiresOn())
                        .append("，负责人 ").append(entry.getValue().owner()).append('\n'));
        return out.toString();
    }
}

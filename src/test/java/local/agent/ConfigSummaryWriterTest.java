package local.agent;

import local.agent.config.AnalyzerConfig;
import local.agent.report.ConfigSummaryWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ConfigSummaryWriterTest {
    @Test void rendersDeterministicEffectiveConfiguration() {
        String summary = new ConfigSummaryWriter().render(Path.of("project"), AnalyzerConfig.defaults(), false);
        assertTrue(summary.startsWith("配置有效\n"));
        assertTrue(summary.contains("来源: 内置默认值"));
        assertTrue(summary.contains("最大文件数: 10000"));
        assertTrue(summary.contains("扣分权重: high=25, medium=12, low=5"));
        assertTrue(summary.contains("禁用规则: 无"));
    }
}

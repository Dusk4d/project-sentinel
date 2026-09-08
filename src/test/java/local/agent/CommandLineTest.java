package local.agent;

import local.agent.cli.CommandLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CommandLineTest {
    @Test void acceptsDocumentedCommandsAndInteractiveWorkspace() {
        assertTrue(CommandLine.validate(new String[0]).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--check", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--plan-json", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--serve", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--serve", "project", "8787"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--tools-json", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--call", "project", "read", "README.md"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--ask", "project", "如何启动"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--ask-json", "project", "如何启动"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--ask-ai", "project", "如何启动"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--agent-ai", "project", "分析启动方式"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80", "5"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily-verify", "project", "state", "80", "120", "5"}).isEmpty());
        assertTrue(CommandLine.usage().contains("最大允许降幅"));
        assertTrue(CommandLine.validate(new String[]{"--state-status", "state"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--init-config", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--validate-config", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--list-rules"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--help"}).isEmpty());
        assertTrue(CommandLine.usage().contains("--check-json <项目>"));
        assertTrue(CommandLine.usage().contains("--call <工作区> <工具名> <输入>"));
        assertTrue(CommandLine.usage().contains("--ask-ai <工作区> <问题>"));
        assertTrue(CommandLine.usage().contains("--agent-ai <工作区> <任务>"));
    }

    @Test void rejectsUnknownMissingAndUnexpectedArguments() {
        assertTrue(CommandLine.validate(new String[]{"--unknown"}).orElseThrow().contains("未知选项"));
        assertTrue(CommandLine.validate(new String[]{"--report", "project"}).orElseThrow().contains("缺少参数"));
        assertTrue(CommandLine.validate(new String[]{"--version", "extra"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"--check", "project", "ignored"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"--serve"}).orElseThrow().contains("缺少参数"));
        assertTrue(CommandLine.validate(new String[]{"--serve", "project", "8787", "extra"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80", "5", "ignored"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"one", "two"}).orElseThrow().contains("最多接受一个"));
    }
}

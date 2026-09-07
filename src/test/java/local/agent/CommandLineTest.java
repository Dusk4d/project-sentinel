package local.agent;

import local.agent.cli.CommandLine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class CommandLineTest {
    @Test void acceptsDocumentedCommandsAndInteractiveWorkspace() {
        assertTrue(CommandLine.validate(new String[0]).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--check", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80", "5"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--daily-verify", "project", "state", "80", "120", "5"}).isEmpty());
        assertTrue(CommandLine.usage().contains("最大允许降幅"));
        assertTrue(CommandLine.validate(new String[]{"--state-status", "state"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--init-config", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--validate-config", "project"}).isEmpty());
        assertTrue(CommandLine.validate(new String[]{"--help"}).isEmpty());
        assertTrue(CommandLine.usage().contains("--check-json <项目>"));
    }

    @Test void rejectsUnknownMissingAndUnexpectedArguments() {
        assertTrue(CommandLine.validate(new String[]{"--unknown"}).orElseThrow().contains("未知选项"));
        assertTrue(CommandLine.validate(new String[]{"--report", "project"}).orElseThrow().contains("缺少参数"));
        assertTrue(CommandLine.validate(new String[]{"--version", "extra"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"--check", "project", "ignored"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"--daily", "project", "state", "80", "5", "ignored"}).orElseThrow().contains("参数过多"));
        assertTrue(CommandLine.validate(new String[]{"one", "two"}).orElseThrow().contains("最多接受一个"));
    }
}

package local.agent.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CommandLine {
    public static final String VERSION = "0.2.0";
    private static final Map<String, CommandSpec> COMMANDS = commands();

    private CommandLine() { }

    public static Optional<String> validate(String[] args) {
        if (args.length == 0) return Optional.empty();
        if (!args[0].startsWith("--"))
            return args.length == 1 ? Optional.empty() : Optional.of("交互工作区模式最多接受一个路径参数");
        CommandSpec spec = COMMANDS.get(args[0]);
        if (spec == null) return Optional.of("未知选项: " + args[0]);
        if (args.length < spec.minimum()) return Optional.of(args[0] + " 缺少参数");
        if (args.length > spec.maximum()) return Optional.of(args[0] + " 参数过多");
        return Optional.empty();
    }

    public static String usage() {
        return """
                Project Sentinel %s（构件: workspace-agent）

                用法:
                  java -jar workspace-agent.jar [工作区]
                  java -jar workspace-agent.jar --check <项目>
                  java -jar workspace-agent.jar --check-json <项目>
                  java -jar workspace-agent.jar --plan <项目>
                  java -jar workspace-agent.jar --plan-json <项目>
                  java -jar workspace-agent.jar --report <项目> <报告目录>
                  java -jar workspace-agent.jar --report-html <项目> <HTML文件>
                  java -jar workspace-agent.jar --serve <工作区> [端口]
                  java -jar workspace-agent.jar --tools-json <工作区>
                  java -jar workspace-agent.jar --call <工作区> <工具名> <输入>
                  java -jar workspace-agent.jar --ask <工作区> <问题>
                  java -jar workspace-agent.jar --ask-json <工作区> <问题>
                  java -jar workspace-agent.jar --ask-ai <工作区> <问题>
                  java -jar workspace-agent.jar --agent-ai <工作区> <任务>
                  java -jar workspace-agent.jar --agent-ai-memory <工作区> <状态目录> <任务>
                  java -jar workspace-agent.jar --portfolio <工作区>
                  java -jar workspace-agent.jar --portfolio-daily <工作区> <状态目录> [最低分]
                  java -jar workspace-agent.jar --snapshot <项目> <历史文件>
                  java -jar workspace-agent.jar --trend <历史文件>
                  java -jar workspace-agent.jar --daily <项目> <状态目录> [最低分] [最大允许降幅]
                  java -jar workspace-agent.jar --verify-build <项目> [超时秒]
                  java -jar workspace-agent.jar --daily-verify <项目> <状态目录> [最低分] [超时秒] [最大允许降幅]
                  java -jar workspace-agent.jar --state-status <状态目录>
                  java -jar workspace-agent.jar --init-config <项目>
                  java -jar workspace-agent.jar --validate-config <项目>
                  java -jar workspace-agent.jar --list-rules
                  java -jar workspace-agent.jar --help
                  java -jar workspace-agent.jar --version
                """.formatted(VERSION);
    }

    private static Map<String, CommandSpec> commands() {
        var result = new LinkedHashMap<String, CommandSpec>();
        result.put("--check", exact(2));
        result.put("--check-json", exact(2));
        result.put("--plan", exact(2));
        result.put("--plan-json", exact(2));
        result.put("--report", exact(3));
        result.put("--report-html", exact(3));
        result.put("--serve", new CommandSpec(2, 3));
        result.put("--tools-json", exact(2));
        result.put("--call", exact(4));
        result.put("--ask", exact(3));
        result.put("--ask-json", exact(3));
        result.put("--ask-ai", exact(3));
        result.put("--agent-ai", exact(3));
        result.put("--agent-ai-memory", exact(4));
        result.put("--portfolio", exact(2));
        result.put("--portfolio-daily", new CommandSpec(3, 4));
        result.put("--snapshot", exact(3));
        result.put("--trend", exact(2));
        result.put("--daily", new CommandSpec(3, 5));
        result.put("--verify-build", new CommandSpec(2, 3));
        result.put("--daily-verify", new CommandSpec(3, 6));
        result.put("--state-status", exact(2));
        result.put("--init-config", exact(2));
        result.put("--validate-config", exact(2));
        result.put("--list-rules", exact(1));
        result.put("--help", exact(1));
        result.put("--version", exact(1));
        return Map.copyOf(result);
    }

    private static CommandSpec exact(int count) { return new CommandSpec(count, count); }
    private record CommandSpec(int minimum, int maximum) { }
}

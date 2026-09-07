package local.agent.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CommandLine {
    public static final String VERSION = "0.2.0";
    private static final Map<String, Integer> MIN_ARGUMENTS = commands();

    private CommandLine() { }

    public static Optional<String> validate(String[] args) {
        if (args.length == 0 || !args[0].startsWith("--")) return Optional.empty();
        if (args[0].equals("--help") || args[0].equals("--version"))
            return args.length == 1 ? Optional.empty() : Optional.of(args[0] + " 不接受额外参数");
        Integer minimum = MIN_ARGUMENTS.get(args[0]);
        if (minimum == null) return Optional.of("未知选项: " + args[0]);
        if (args.length < minimum) return Optional.of(args[0] + " 缺少参数");
        return Optional.empty();
    }

    public static String usage() {
        return """
                Workspace Agent %s

                用法:
                  java -jar workspace-agent.jar [工作区]
                  java -jar workspace-agent.jar --check <项目>
                  java -jar workspace-agent.jar --check-json <项目>
                  java -jar workspace-agent.jar --plan <项目>
                  java -jar workspace-agent.jar --report <项目> <报告目录>
                  java -jar workspace-agent.jar --portfolio <工作区>
                  java -jar workspace-agent.jar --snapshot <项目> <历史文件>
                  java -jar workspace-agent.jar --trend <历史文件>
                  java -jar workspace-agent.jar --daily <项目> <状态目录> [最低分]
                  java -jar workspace-agent.jar --help
                  java -jar workspace-agent.jar --version
                """.formatted(VERSION);
    }

    private static Map<String, Integer> commands() {
        var result = new LinkedHashMap<String, Integer>();
        result.put("--check", 2);
        result.put("--check-json", 2);
        result.put("--plan", 2);
        result.put("--report", 3);
        result.put("--portfolio", 2);
        result.put("--snapshot", 3);
        result.put("--trend", 2);
        result.put("--daily", 3);
        return Map.copyOf(result);
    }
}

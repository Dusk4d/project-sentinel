package local.agent;

import local.agent.tools.ListFilesTool;
import local.agent.tools.ReadFileTool;
import local.agent.tools.SearchTextTool;
import local.agent.tools.HealthCheckTool;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkspaceAgent {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public WorkspaceAgent(Path workspace) {
        var guard = new WorkspaceGuard(workspace);
        register(new ListFilesTool(guard));
        register(new ReadFileTool(guard));
        register(new SearchTextTool(guard));
        register(new HealthCheckTool(guard));
    }

    private void register(Tool tool) { tools.put(tool.name(), tool); }

    public String run(String request) {
        String text = request == null ? "" : request.trim();
        if (text.isEmpty()) return "请输入任务。输入“帮助”查看示例。";
        if (text.equalsIgnoreCase("帮助") || text.equalsIgnoreCase("help")) return help();
        if (text.startsWith("列出") || text.toLowerCase().startsWith("list"))
            return execute("list", argumentAfterCommand(text, "列出", "list"));
        if (text.startsWith("读取") || text.toLowerCase().startsWith("read"))
            return execute("read", argumentAfterCommand(text, "读取", "read"));
        if (text.startsWith("搜索") || text.toLowerCase().startsWith("search"))
            return execute("search", argumentAfterCommand(text, "搜索", "search"));
        if (text.startsWith("体检") || text.toLowerCase().startsWith("health"))
            return execute("health", argumentAfterCommand(text, "体检", "health"));
        return "我尚不能可靠执行这个任务。当前工具：\n" + help();
    }

    private String execute(String name, String input) {
        ToolResult result = tools.get(name).execute(input);
        return (result.success() ? "[完成] " : "[拒绝/失败] ") + result.output();
    }

    private String argumentAfterCommand(String text, String chinese, String english) {
        String lower = text.toLowerCase();
        int length = lower.startsWith(english) ? english.length() : chinese.length();
        String arg = text.substring(length).trim();
        return arg.equals("文件") || arg.equals("目录") ? "." : arg;
    }

    private String help() {
        return "体检 [项目子目录]\n列出文件 [子目录]\n读取 <相对路径>\n搜索 <关键词>\n退出";
    }
}

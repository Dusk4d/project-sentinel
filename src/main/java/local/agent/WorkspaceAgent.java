package local.agent;

import local.agent.tools.ListFilesTool;
import local.agent.tools.ReadFileTool;
import local.agent.tools.SearchTextTool;
import local.agent.tools.HealthCheckTool;
import local.agent.tools.RagQueryTool;
import local.agent.function.FunctionCallResult;
import local.agent.function.FunctionRegistry;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorkspaceAgent {
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final FunctionRegistry functions = new FunctionRegistry();

    public WorkspaceAgent(Path workspace) {
        var guard = new WorkspaceGuard(workspace);
        register(new ListFilesTool(guard), "要列出的工作区相对目录，根目录使用 .");
        register(new ReadFileTool(guard), "要读取的工作区相对文件路径");
        register(new SearchTextTool(guard), "要在工作区中搜索的文本关键词");
        register(new HealthCheckTool(guard), "要体检的工作区相对子目录，根目录使用 .");
        register(new RagQueryTool(guard), "关于项目代码或文档的问题");
    }

    private void register(Tool tool, String inputDescription) {
        tools.put(tool.name(), tool);
        functions.register(tool, inputDescription);
    }

    public String toolDefinitionsJson() { return functions.definitionsJson(); }

    public FunctionCallResult callFunction(String callId, String name, String input) {
        return functions.call(callId, name, input);
    }

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
        if (text.startsWith("问答") || text.toLowerCase().startsWith("ask"))
            return execute("rag_query", argumentAfterCommand(text, "问答", "ask"));
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
        return "体检 [项目子目录]\n列出文件 [子目录]\n读取 <相对路径>\n搜索 <关键词>\n问答 <关于项目的问题>\n退出";
    }
}

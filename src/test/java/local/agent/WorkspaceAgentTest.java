package local.agent;

import java.nio.file.Files;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.report.ReportStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class WorkspaceAgentTest {
    @TempDir Path root;
    @Test void executesReadOnlyWorkflowAndWritesReport() throws Exception {
        Files.writeString(root.resolve("note.txt"), "hello agent\nTODO improve\n");
        var agent = new WorkspaceAgent(root);
        assertTrue(agent.run("列出文件").contains("note.txt"));
        assertTrue(agent.run("读取 note.txt").contains("hello agent"));
        assertTrue(agent.run("搜索 TODO").contains("note.txt:2"));
        assertTrue(agent.run("读取 ../secret.txt").contains("路径超出工作区"));
        assertTrue(agent.run("体检").contains("项目健康报告"));
        assertTrue(agent.run("体检").contains("缺少可识别的构建清单"));
        assertTrue(agent.toolDefinitionsJson().contains("\"name\":\"health\""));
        var call = agent.callFunction("call-health", "read", "note.txt");
        assertTrue(call.success());
        assertEquals("call-health", call.callId());
        assertTrue(call.output().contains("hello agent"));
        assertTrue(agent.run("问答 hello 是什么").contains("note.txt"));
        assertTrue(agent.toolDefinitionsJson().contains("\"name\":\"rag_query\""));
        var report = new ReportStore().save(new ProjectAnalyzer().analyze(root), root.resolve("reports"));
        assertTrue(Files.isRegularFile(report));
        assertTrue(Files.readString(report).contains("健康分"));
    }
}

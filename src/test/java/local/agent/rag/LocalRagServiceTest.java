package local.agent.rag;

import local.agent.WorkspaceGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class LocalRagServiceTest {
    @TempDir Path workspace;

    @Test void retrievesRelevantChineseEvidenceWithRelativePathAndLines() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# 星图系统\n\n使用 start-web.cmd 启动本地健康检测看板。\n默认端口是 8787。\n");
        Files.writeString(workspace.resolve("notes.md"), "数据库采用 SQLite 保存任务历史。\n");
        var answer = new LocalRagService(new WorkspaceGuard(workspace)).ask("怎么启动健康检测看板？");
        assertFalse(answer.evidence().isEmpty());
        assertEquals("README.md", answer.evidence().get(0).path());
        assertTrue(answer.evidence().get(0).text().contains("start-web.cmd"));
        assertTrue(answer.renderText().contains("README.md:1-4"));
        assertTrue(answer.toJson().contains("\"schemaVersion\": 1"));
    }

    @Test void excludesSensitiveAndBuildOutputFiles() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "普通项目说明");
        Files.writeString(workspace.resolve(".env"), "SPECIAL_SECRET_TOKEN=hidden");
        Path target = Files.createDirectories(workspace.resolve("target"));
        Files.writeString(target.resolve("generated.txt"), "SPECIAL_SECRET_TOKEN generated");
        var answer = new LocalRagService(new WorkspaceGuard(workspace)).ask("SPECIAL_SECRET_TOKEN");
        assertTrue(answer.evidence().isEmpty());
        assertTrue(answer.answer().contains("没有找到"));
    }

    @Test void rejectsBlankQuestion() {
        var rag = new LocalRagService(new WorkspaceGuard(workspace));
        assertThrows(IllegalArgumentException.class, () -> rag.ask("  "));
    }
}

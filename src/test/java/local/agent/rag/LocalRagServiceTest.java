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

    @Test void prioritizesRootReadmeForBroadProjectOverviewQuestions() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Finance Agent\n这是一个面向个人理财的预算分析与风险提示系统。\n"
                + "其他介绍。\n".repeat(8) + "## 目录\n- [功能特性](#功能特性)\n- [技术栈](#技术栈)\n");
        Path interview = Files.createDirectories(workspace.resolve("interview-prep"));
        Files.writeString(interview.resolve("高频问答.md"), "项目主要功能是什么？回答时先讲项目，再讲功能。\n".repeat(20));
        Files.writeString(workspace.resolve("Service.java"), "class Service { void projectFunction() {} }\n");
        Files.writeString(workspace.resolve("IntentClassifier.java"),
                "String overview = \"主要功能 核心功能 项目是什么 做什么 项目介绍 项目简介 overview purpose readme 简介 定位 目标\";\n".repeat(8));

        var answer = new LocalRagService(new WorkspaceGuard(workspace)).ask("这个项目主要功能是什么？");

        assertFalse(answer.evidence().isEmpty());
        assertEquals("README.md", answer.evidence().get(0).path());
        assertEquals(1, answer.evidence().get(0).startLine());
        assertTrue(answer.answer().contains("个人理财"));
        assertFalse(answer.answer().contains("<div"));
        assertFalse(answer.answer().contains("](#"));
    }

    @Test void preservesSourceCodePrecisionForSpecificSymbolQueries() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "项目介绍和主要功能。\n");
        Files.writeString(workspace.resolve("PaymentService.java"), "class PaymentService { void reconcileLedger() {} }\n");

        var answer = new LocalRagService(new WorkspaceGuard(workspace)).ask("reconcileLedger 在哪里？");

        assertEquals("PaymentService.java", answer.evidence().get(0).path());
    }

    @Test void reusesUnchangedChunksAndInvalidatesChangedOrDeletedFiles() throws Exception {
        Path readme = workspace.resolve("README.md");
        Files.writeString(readme, "alphaFeature 项目说明\n");
        var rag = new LocalRagService(new WorkspaceGuard(workspace));

        assertFalse(rag.ask("alphaFeature").evidence().isEmpty());
        long firstBuild = rag.indexedFiles();
        assertEquals(1, firstBuild);
        assertFalse(rag.ask("alphaFeature").evidence().isEmpty());
        assertEquals(firstBuild, rag.indexedFiles(), "未变化文件不得重复读取和分块");

        Files.writeString(readme, "betaFeature 已更新项目说明，长度不同\n");
        assertFalse(rag.ask("betaFeature").evidence().isEmpty());
        assertEquals(firstBuild + 1, rag.indexedFiles(), "变化文件必须重建分块");

        Files.delete(readme);
        assertTrue(rag.ask("betaFeature").evidence().isEmpty(), "已删除文件不得残留在索引中");
    }

    @Test void reportsFileLimitOnlyAfterConfirmingAdditionalIndexableFile() throws Exception {
        for (int index = 0; index < 1_000; index++)
            Files.writeString(workspace.resolve("doc-" + index + ".md"), "sharedEvidence");
        var exact = new LocalRagService(new WorkspaceGuard(workspace)).ask("sharedEvidence");
        assertEquals(1_000, exact.indexedFiles());
        assertFalse(exact.indexTruncated(), "恰好达到文件上限不应误报截断");

        Files.writeString(workspace.resolve("overflow.md"), "sharedEvidence");
        var overflow = new LocalRagService(new WorkspaceGuard(workspace)).ask("sharedEvidence");
        assertEquals(1_000, overflow.indexedFiles());
        assertTrue(overflow.indexTruncated());
        assertTrue(overflow.answer().contains("结果可能不完整"));
        assertTrue(overflow.toJson().contains("\"indexTruncated\": true"));
    }

    @Test void reportsChunkLimitOnlyAfterConfirmingAdditionalChunkInOneFile() throws Exception {
        Path large = workspace.resolve("large.txt");
        Files.writeString(large, "ab\n".repeat(40_002));
        var exact = new LocalRagService(new WorkspaceGuard(workspace)).ask("ab");
        assertEquals(5_000, exact.indexedChunks());
        assertFalse(exact.indexTruncated(), "恰好达到分块上限不应误报截断");

        Files.writeString(large, "ab\n".repeat(40_003));
        var overflow = new LocalRagService(new WorkspaceGuard(workspace)).ask("ab");
        assertEquals(5_000, overflow.indexedChunks());
        assertTrue(overflow.indexTruncated(), "确认第 5001 个分块后必须报告截断");
    }
}

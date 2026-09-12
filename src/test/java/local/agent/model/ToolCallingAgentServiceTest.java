package local.agent.model;

import com.sun.net.httpserver.HttpServer;
import local.agent.WorkspaceAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class ToolCallingAgentServiceTest {
    @TempDir Path workspace;

    @Test void letsModelChooseReadToolThenReturnsEvidenceBasedAnswer() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "运行 start-web.cmd 后访问 8787。", StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"运行 start-web.cmd，然后访问 8787。\"}}]}"
        );
        var server = scriptedServer(responses, requests);
        try {
            var result = service(server).run("这个项目怎么启动？");
            assertEquals(2, result.modelRounds());
            assertEquals(1, result.toolCalls());
            assertEquals(List.of(new AgentToolTrace(1, "read", "README.md", true)), result.trace());
            assertTrue(result.answer().contains("8787"));
            assertTrue(requests.get(0).contains("\"tools\":["));
            assertTrue(requests.get(0).contains("\"name\":\"read\""));
            assertTrue(requests.get(1).contains("\"role\":\"tool\""));
            assertTrue(requests.get(1).contains("\"role\":\"assistant\""));
            assertTrue(requests.get(1).contains("start-web.cmd"));
            assertTrue(requests.get(1).contains("\"tool_call_id\":\"call_1\""));
            assertTrue(result.renderText().contains("read (README.md) [成功]"));
            assertTrue(result.toJson().contains("\"input\":\"README.md\""));
        } finally { server.stop(0); }
    }

    @Test void rejectsToolArgumentsOutsideClosedInputSchema() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo");
        String response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"c1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"path\\\":\\\"README.md\\\"}\"}}]}}]}";
        var server = scriptedServer(List.of(response), new ArrayList<>());
        try {
            var failure = assertThrows(java.io.IOException.class, () -> service(server).run("读取说明"));
            assertTrue(failure.getMessage().contains("字符串字段 input"));
        } finally { server.stop(0); }
    }

    @Test void stopsModelsThatNeverFinishWithinRoundLimit() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo");
        String response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"c1\",\"function\":{\"name\":\"list\",\"arguments\":\"{\\\"input\\\":\\\".\\\"}\"}}]}}]}";
        var server = scriptedServer(List.of(response, response, response, response, response), new ArrayList<>());
        try {
            var failure = assertThrows(java.io.IOException.class, () -> service(server).run("一直调用工具"));
            assertTrue(failure.getMessage().contains("5 轮"));
        } finally { server.stop(0); }
    }

    @Test void includesBoundedConversationMemoryBeforeCurrentTask() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo");
        var requests = new ArrayList<String>();
        var server = scriptedServer(List.of(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"memory_1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"新的回答\"}}]}"), requests);
        try {
            var history = List.of(new AgentMemoryEntry(java.time.Instant.parse("2026-01-01T00:00:00Z"),
                    "旧问题", "旧答案", 1, 0));
            var result = service(server).run("追问", history);
            assertEquals("新的回答", result.answer());
            assertTrue(requests.get(0).contains("历史任务：旧问题"));
            assertTrue(requests.get(0).contains("历史回答：旧答案"));
            assertTrue(requests.get(0).indexOf("历史任务") < requests.get(0).indexOf("追问"));
        } finally { server.stop(0); }
    }

    @Test void requiresSuccessfulProjectEvidenceBeforeAcceptingFinalAnswer() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "真实项目说明", StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var server = scriptedServer(List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"未经检查的猜测\"}}]}",
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"ground_1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"基于真实项目说明的回答\"}}]}"), requests);
        try {
            var result = service(server).run("这个项目是什么？");
            assertEquals("基于真实项目说明的回答", result.answer());
            assertEquals(3, result.modelRounds());
            assertEquals(1, result.toolCalls());
            assertTrue(requests.get(1).contains("必须获取足以支持结论的当前工作区证据"));
            assertTrue(requests.get(2).contains("真实项目说明"));
        } finally { server.stop(0); }
    }

    @Test void listAloneIsInsufficientForAContentQuestion() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "使用 start-web.cmd 启动", StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var server = scriptedServer(List.of(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"list_1\",\"function\":{\"name\":\"list\",\"arguments\":\"{\\\"input\\\":\\\".\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"看到脚本，直接猜测启动方式\"}}]}",
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"read_1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"运行 start-web.cmd。\"}}]}"), requests);
        try {
            var result = service(server).run("项目如何启动？");
            assertEquals(4, result.modelRounds());
            assertEquals(2, result.toolCalls());
            assertEquals(List.of("list", "read"), result.trace().stream().map(AgentToolTrace::name).toList());
            assertTrue(requests.get(2).contains("仅调用 list 不足以证明文件内容"));
        } finally { server.stop(0); }
    }

    @Test void listIsSufficientWhenTaskOnlyRequestsAFileListing() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo", StandardCharsets.UTF_8);
        var server = scriptedServer(List.of(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"list_only_1\",\"function\":{\"name\":\"list\",\"arguments\":\"{\\\"input\\\":\\\".\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"根目录包含 README.md。\"}}]}"), new ArrayList<>());
        try {
            var result = service(server).run("列出根目录文件");
            assertEquals(2, result.modelRounds());
            assertEquals(1, result.toolCalls());
        } finally { server.stop(0); }
    }

    @Test void successfulToolWithoutEvidenceCannotGroundFinalAnswer() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "真实项目说明", StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var server = scriptedServer(List.of(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"empty_1\",\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"input\\\":\\\"ABSENT_SYMBOL\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"搜索执行成功，所以直接猜测\"}}]}",
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"read_1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"content\":\"基于真实项目说明回答\"}}]}"), requests);
        try {
            var result = service(server).run("分析不存在的符号");
            assertEquals(4, result.modelRounds());
            assertEquals(2, result.toolCalls());
            assertTrue(result.trace().get(0).success());
            assertFalse(result.trace().get(0).evidence());
            assertTrue(result.trace().get(1).evidence());
            assertTrue(requests.get(2).contains("必须获取足以支持结论"));
            assertTrue(result.toJson().contains("\"evidence\":false"));
        } finally { server.stop(0); }
    }

    @Test void resumesAfterModelFailureWithoutRepeatingCompletedToolRound() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "运行 start-web.cmd 后访问 8787。", StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var responses = List.of(
                "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"resume_1\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "not-json",
                "{\"choices\":[{\"message\":{\"content\":\"从检查点恢复完成。\"}}]}"
        );
        var server = scriptedServer(responses, requests);
        Path state = workspace.resolve("state");
        try {
            var checkpoints = new AgentCheckpointStore(workspace, state);
            assertThrows(java.io.IOException.class,
                    () -> service(server).runResumable("检查启动方式", List.of(), checkpoints));
            AgentCheckpoint saved = checkpoints.load("检查启动方式").orElseThrow();
            assertEquals(1, saved.completedRounds());
            assertEquals(1, saved.toolCalls());
            var result = service(server).runResumable("检查启动方式", List.of(), checkpoints);
            assertEquals("从检查点恢复完成。", result.answer());
            assertEquals(2, result.modelRounds());
            assertEquals(1, result.toolCalls());
            assertEquals(3, requests.size());
            assertTrue(requests.get(2).contains("\"role\":\"tool\""));
            assertTrue(requests.get(2).contains("start-web.cmd"));
            assertEquals(result, checkpoints.loadPendingResult("检查启动方式").orElseThrow());
            assertEquals(result, service(server).runResumable("检查启动方式", List.of(), checkpoints));
            assertEquals(3, requests.size(), "待写入记忆的最终结果必须直接恢复，不得再次调用模型");
            checkpoints.markCompleted("检查启动方式");
            assertTrue(checkpoints.load("新任务").isEmpty());
        } finally { server.stop(0); }
    }

    @Test void boundsRepeatedLargeToolResultsBeforeSendingThemBackToModel() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "中".repeat(30_000), StandardCharsets.UTF_8);
        var requests = new ArrayList<String>();
        var server = scriptedServer(List.of(
                toolCallsResponse("a", 8),
                toolCallsResponse("b", 8),
                toolCallsResponse("c", 4),
                "{\"choices\":[{\"message\":{\"content\":\"已读取并分析大文件。\"}}]}"), requests);
        try {
            var result = service(server).run("分析 large.txt 的内容");
            assertEquals(4, result.modelRounds());
            assertEquals(20, result.toolCalls());
            assertEquals(4, requests.size());
            assertTrue(requests.stream().allMatch(request ->
                    request.getBytes(StandardCharsets.UTF_8).length < OpenAiCompatibleClient.MAX_REQUEST_BYTES));
            assertTrue(requests.get(1).contains("Agent 已按上下文预算截断工具输出"));
            assertTrue(requests.get(3).getBytes(StandardCharsets.UTF_8).length < 500 * 1024,
                    "20 次大文件读取也应保留充足的模型请求余量");
        } finally { server.stop(0); }
    }

    private static String toolCallsResponse(String prefix, int count) {
        var calls = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            calls.add("{\"id\":\"" + prefix + i
                    + "\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"large.txt\\\"}\"}}");
        }
        return "{\"choices\":[{\"message\":{\"tool_calls\":[" + String.join(",", calls) + "]}}]}";
    }

    private ToolCallingAgentService service(HttpServer server) {
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        return new ToolCallingAgentService(new WorkspaceAgent(workspace), new OpenAiCompatibleClient(config));
    }

    private HttpServer scriptedServer(List<String> responses, List<String> requests) throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int index = count.getAndIncrement();
            byte[] bytes = responses.get(Math.min(index, responses.size() - 1)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}

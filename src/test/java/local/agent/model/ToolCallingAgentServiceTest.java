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
            assertEquals(List.of(new AgentToolTrace(1, "read", true)), result.trace());
            assertTrue(result.answer().contains("8787"));
            assertTrue(requests.get(0).contains("\"tools\":["));
            assertTrue(requests.get(0).contains("\"name\":\"read\""));
            assertTrue(requests.get(1).contains("\"role\":\"tool\""));
            assertTrue(requests.get(1).contains("start-web.cmd"));
            assertTrue(requests.get(1).contains("\"tool_call_id\":\"call_1\""));
            assertTrue(result.renderText().contains("read [成功]"));
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
        var server = scriptedServer(List.of("{\"choices\":[{\"message\":{\"content\":\"新的回答\"}}]}"), requests);
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

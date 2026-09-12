package local.agent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

final class OpenAiCompatibleClientTest {
    @Test void sendsCompatibleRequestAndParsesEscapedContent() throws Exception {
        var requestBody = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"第一行\\n第二行\\u3002\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions"),
                    "demo-model", "secret", Duration.ofSeconds(3));
            String result = new OpenAiCompatibleClient(config).complete("system", "用户问题");
            assertEquals("第一行\n第二行。", result);
            assertEquals("Bearer secret", authorization.get());
            assertTrue(requestBody.get().contains("\"model\":\"demo-model\""));
            assertTrue(requestBody.get().contains("用户问题"));
            assertFalse(requestBody.get().contains("secret"));
        } finally { server.stop(0); }
    }

    @Test void rejectsMalformedAndFailedResponses() throws Exception {
        assertThrows(java.io.IOException.class, () -> OpenAiCompatibleClient.extractContent("{\"choices\":[]}"));
        assertThrows(java.io.IOException.class, () -> OpenAiCompatibleClient.extractContent(
                "{\"choices\":[{\"message\":{\"content\":\"bad\\q\"}}]}"));
    }

    @Test void rejectsOversizedRequestBeforeContactingModel() throws Exception {
        var contacted = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> { contacted.set(true); exchange.close(); });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test", "", Duration.ofSeconds(3));
            var client = new OpenAiCompatibleClient(config);
            var failure = assertThrows(java.io.IOException.class,
                    () -> client.complete("system", "中".repeat(OpenAiCompatibleClient.MAX_REQUEST_BYTES)));
            assertTrue(failure.getMessage().contains("1 MiB"));
            assertFalse(contacted.get());
        } finally { server.stop(0); }
    }

    @Test void includesBoundedStructuredModelErrorDetail() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":{\"message\":\"tool message requires tool_call_id\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test", "", Duration.ofSeconds(3));
            var failure = assertThrows(java.io.IOException.class,
                    () -> new OpenAiCompatibleClient(config).complete("system", "question"));
            assertEquals("模型服务返回 HTTP 400：tool message requires tool_call_id", failure.getMessage());
        } finally { server.stop(0); }
    }

    @Test void removesResponseOnlyToolCallIndexBeforeNextModelRound() throws Exception {
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,"
                    + "\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\","
                    + "\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test", "", Duration.ofSeconds(3));
            var turn = new OpenAiCompatibleClient(config).completeTurn("system",
                    List.of("{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"index\":0.0,"
                            + "\"id\":\"old\",\"type\":\"function\",\"function\":{\"name\":\"read\","
                            + "\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}",
                            "{\"role\":\"tool\",\"tool_call_id\":\"old\",\"content\":\"text\"}"), "[]");
            assertEquals(1, turn.toolCalls().size());
            assertFalse(requestBody.get().contains("\"index\""));
            assertFalse(turn.assistantMessageJson().contains("\"index\""));
            assertTrue(turn.assistantMessageJson().contains("\"id\":\"call_1\""));
            assertTrue(turn.assistantMessageJson().contains("\"type\":\"function\""));
        } finally { server.stop(0); }
    }

    @Test void streamsSseDeltasInOrderAndReturnsTheCompleteAnswer() throws Exception {
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"，项目\"}}]}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test", "", Duration.ofSeconds(3));
            var deltas = new ArrayList<String>();
            String answer = new OpenAiCompatibleClient(config).completeStreaming("system", "question", deltas::add);
            assertEquals("你好，项目", answer);
            assertEquals(List.of("你好", "，项目"), deltas);
            assertTrue(requestBody.get().contains("\"stream\":true"));
        } finally { server.stop(0); }
    }

    @Test void rejectsOversizedSingleStreamingEvent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(("data: " + "x".repeat(257 * 1024)).getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                    "test", "", Duration.ofSeconds(3));
            var failure = assertThrows(java.io.IOException.class,
                    () -> new OpenAiCompatibleClient(config).completeStreaming("system", "question", ignored -> { }));
            assertTrue(failure.getMessage().contains("256 KiB"));
        } finally { server.stop(0); }
    }
}

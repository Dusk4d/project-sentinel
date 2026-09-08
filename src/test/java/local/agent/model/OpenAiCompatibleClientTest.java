package local.agent.model;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

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
}

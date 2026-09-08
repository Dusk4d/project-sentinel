package local.agent.web;

import com.sun.net.httpserver.HttpServer;
import local.agent.model.ModelConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

final class WebAiRagTest {
    @TempDir Path workspace;

    @Test void reportsDisabledModelAndRejectsAiEndpointWithoutConfiguration() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "使用 start-web.cmd 启动。", StandardCharsets.UTF_8);
        try (var web = new LocalWebServer(workspace, 0, new ScanAdmissionGate());
             var client = HttpClient.newHttpClient()) {
            web.start();
            var projects = client.send(HttpRequest.newBuilder(URI.create(web.url() + "api/projects")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue(projects.body().contains("\"modelEnabled\":false"));
            var response = post(client, web.url() + "api/rag-ai", "如何启动？");
            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("not configured"));
        }
    }

    @Test void returnsModelAnswerAndEvidenceWhenConfigured() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "使用 start-web.cmd 启动。", StandardCharsets.UTF_8);
        var model = modelServer(200, "{\"choices\":[{\"message\":{\"content\":\"请运行脚本 [README.md:1-1]\"}}]}");
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + model.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        try (var web = new LocalWebServer(workspace, 0, config); var client = HttpClient.newHttpClient()) {
            web.start();
            var response = post(client, web.url() + "api/rag-ai", "如何启动？");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"modelUsed\": true"));
            assertTrue(response.body().contains("请运行脚本"));
            assertTrue(response.body().contains("\"path\":\"README.md\""));
        } finally { model.stop(0); }
    }

    @Test void returnsSuccessfulFallbackPayloadWhenConfiguredModelFails() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "使用 start-web.cmd 启动。", StandardCharsets.UTF_8);
        var model = modelServer(503, "unavailable");
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + model.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        try (var web = new LocalWebServer(workspace, 0, config); var client = HttpClient.newHttpClient()) {
            web.start();
            var response = post(client, web.url() + "api/rag-ai", "如何启动？");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"modelUsed\": false"));
            assertTrue(response.body().contains("HTTP 503"));
            assertTrue(response.body().contains("start-web.cmd"));
        } finally { model.stop(0); }
    }

    private HttpResponse<String> post(HttpClient client, String endpoint, String question) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "text/plain; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(question, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpServer modelServer(int status, String body) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}

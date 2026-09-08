package local.agent;

import local.agent.web.LocalWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RagWebTest {
    @TempDir Path workspace;

    @Test void answersFromSelectedLocalProjectWithEvidence() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Demo\n使用 start-web.cmd 启动健康看板，默认端口 8787。\n");
        try (var server = new LocalWebServer(workspace, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var request = HttpRequest.newBuilder(URI.create(server.url() + "api/rag"))
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("怎么启动健康看板？", StandardCharsets.UTF_8)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"schemaVersion\": 1"));
            assertTrue(response.body().contains("\"path\":\"README.md\""));
            assertTrue(response.body().contains("start-web.cmd"));
        }
    }

    @Test void rejectsWrongMediaTypeBlankAndOversizedQuestions() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Demo");
        try (var server = new LocalWebServer(workspace, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            URI endpoint = URI.create(server.url() + "api/rag");
            var wrongType = client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(415, wrongType.statusCode());

            var blank = client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString("  ")).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(400, blank.statusCode());

            var oversized = client.send(HttpRequest.newBuilder(endpoint).header("Content-Type", "text/plain")
                            .POST(HttpRequest.BodyPublishers.ofString("x".repeat(8 * 1024 + 1))).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(413, oversized.statusCode());
        }
    }
}

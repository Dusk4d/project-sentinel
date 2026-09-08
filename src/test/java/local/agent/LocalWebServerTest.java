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

final class LocalWebServerTest {
    @TempDir Path project;

    @Test void servesUtf8DashboardHealthAndLiveReportOnLoopback() throws Exception {
        Files.writeString(project.resolve("README.md"), "# 示例");
        try (var server = new LocalWebServer(project, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            assertTrue(server.port() > 0);
            assertTrue(server.url().startsWith("http://127.0.0.1:"));

            var page = client.send(HttpRequest.newBuilder(URI.create(server.url())).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, page.statusCode());
            assertTrue(page.headers().firstValue("content-type").orElseThrow().contains("charset=utf-8"));
            assertTrue(page.body().contains("重新扫描"));

            var health = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""));

            var report = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/report"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, report.statusCode());
            assertTrue(report.body().contains("\"schemaVersion\": 1"));
            assertTrue(report.body().contains("\"project\": \"" + project.getFileName() + "\""));
        }
    }

    @Test void rejectsUnknownRoutesAndUnsupportedMethods() throws Exception {
        try (var server = new LocalWebServer(project, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var missing = client.send(HttpRequest.newBuilder(URI.create(server.url() + "missing")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            var method = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/health"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, missing.statusCode());
            assertEquals(405, method.statusCode());
        }
    }
}

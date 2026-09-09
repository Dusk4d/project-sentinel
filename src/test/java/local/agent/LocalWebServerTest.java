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
            assertTrue(page.body().contains("上传 ZIP 检测"));
            assertTrue(page.body().contains("id=\"download\" disabled>下载 JSON"));
            assertTrue(page.body().contains("-sentinel-analysis.json"));
            assertTrue(page.body().contains("项目智能助手"));
            assertTrue(page.body().contains("id=\"question\""));
            assertTrue(page.body().contains("'/api/rag'"));
            assertTrue(page.body().contains("id=\"ai\" type=\"checkbox\" disabled"));
            assertTrue(page.body().contains("'/api/rag-ai'"));
            assertTrue(page.body().contains("id=\"agent\" disabled>Agent 分析"));
            assertTrue(page.body().contains("/api/agent-ai"));

            var health = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"status\":\"UP\""));
            assertTrue(health.body().contains("\"projectCount\":1"));
            assertTrue(health.body().contains("\"projectsTruncated\":false"));
            assertTrue(health.body().contains("\"scanBusy\":false"));
            assertTrue(health.body().contains("\"modelEnabled\":false"));
            assertTrue(health.body().contains("\"agentMemoryEnabled\":false"));

            var agentState = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/agent-state")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, agentState.statusCode());
            assertEquals("{\"enabled\":false,\"memoryEntries\":0,\"checkpoint\":\"NONE\"}\n", agentState.body());

            var report = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/report"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, report.statusCode());
            assertTrue(report.body().contains("\"schemaVersion\": 1"));
            assertTrue(report.body().contains("\"project\": \"" + project.getFileName() + "\""));

            var analysis = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/analysis"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, analysis.statusCode());
            assertTrue(analysis.body().contains("\"report\": {"));
            assertTrue(analysis.body().contains("\"plan\": {"));
        }
    }

    @Test void discoversProjectsAndRejectsIdsOutsideTheStartupAllowlist() throws Exception {
        Path first = Files.createDirectories(project.resolve("course/first"));
        Path second = Files.createDirectories(project.resolve("course/second"));
        Files.writeString(first.resolve("pom.xml"), "<project/>");
        Files.writeString(second.resolve("package.json"), "{}");
        try (var server = new LocalWebServer(project, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var projects = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/projects")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, projects.statusCode());
            assertTrue(projects.body().contains("\"name\":\"first\""));
            assertTrue(projects.body().contains("\"name\":\"second\""));
            assertTrue(projects.body().contains("\"truncated\":false"));
            assertTrue(projects.body().contains("\"maximumProjects\":200"));

            var rejected = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/report?project=root"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(400, rejected.statusCode());
            assertTrue(rejected.body().contains("unknown project id"));

            var malformed = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/report?project=%25ZZ"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(400, malformed.statusCode());
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

    @Test void allowsExternalAgentStateWhileModelRemainsOptional() throws Exception {
        Path scanned = Files.createDirectory(project.resolve("scanned"));
        Path state = project.resolve("state");
        try (var server = new LocalWebServer(scanned, 0, state); var client = HttpClient.newHttpClient()) {
            server.start();
            assertFalse(Files.exists(state));
            var health = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue(health.body().contains("\"modelEnabled\":false"));
            assertTrue(health.body().contains("\"agentMemoryEnabled\":true"));
        }
    }
}

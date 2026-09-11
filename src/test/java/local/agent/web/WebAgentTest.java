package local.agent.web;

import com.sun.net.httpserver.HttpServer;
import local.agent.model.ModelConfig;
import local.agent.model.AgentMemoryStore;
import local.agent.model.AgentCheckpointStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class WebAgentTest {
    @TempDir Path workspace;

    @Test void rejectsAgentEndpointWithoutModelConfiguration() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo", StandardCharsets.UTF_8);
        try (var web = new LocalWebServer(workspace, 0, new ScanAdmissionGate());
             var client = HttpClient.newHttpClient()) {
            web.start();
            var response = post(client, web.url() + "api/agent-ai", "检查项目");
            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("not configured"));
        }
    }

    @Test void executesModelSelectedToolAndReturnsStructuredResult() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "运行 start-web.cmd 后访问 8787。", StandardCharsets.UTF_8);
        var responses = List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"web_1\",\"type\":\"function\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"使用 start-web.cmd 启动。\"}}]}"
        );
        var model = scriptedModel(responses);
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + model.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        try (var web = new LocalWebServer(workspace, 0, config); var client = HttpClient.newHttpClient()) {
            web.start();
            var response = post(client, web.url() + "api/agent-ai", "检查如何启动");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"schemaVersion\":1"));
            assertTrue(response.body().contains("start-web.cmd"));
            assertTrue(response.body().contains("\"modelRounds\":2"));
            assertTrue(response.body().contains("\"toolCalls\":1"));
            assertTrue(response.body().contains("\"trace\":[{\"sequence\":1,\"name\":\"read\",\"input\":\"README.md\",\"success\":true}]"));
        } finally { model.stop(0); }
    }

    @Test void exposesAgentControlOnlyWhenModelIsConfigured() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo", StandardCharsets.UTF_8);
        try (var web = new LocalWebServer(workspace, 0, new ScanAdmissionGate());
             var client = HttpClient.newHttpClient()) {
            web.start();
            var response = client.send(HttpRequest.newBuilder(URI.create(web.url())).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("id=\"agent\""));
            assertTrue(response.body().contains("/api/agent-ai"));
            assertTrue(response.body().contains("Agent 分析"));
            assertTrue(response.body().contains("id=\"agent-state\""));
            assertTrue(response.body().contains("/api/agent-state"));
        }
    }

    @Test void executesAgentToolsAgainstUploadedZip() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "host", StandardCharsets.UTF_8);
        var model = scriptedModel(List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"zip_1\",\"type\":\"function\",\"function\":{\"name\":\"read\",\"arguments\":\"{\\\"input\\\":\\\"README.md\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ZIP 项目通过 upload-entry 启动。\"}}]}"));
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + model.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        try (var web = new LocalWebServer(workspace, 0, config); var client = HttpClient.newHttpClient()) {
            web.start();
            byte[] archive;
            try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("uploaded/README.md"));
                zip.write("使用 upload-entry 启动。".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.finish();
                archive = bytes.toByteArray();
            }
            var uploaded = client.send(HttpRequest.newBuilder(URI.create(web.url() + "api/upload-analysis"))
                            .header("Content-Type", "application/zip")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(archive)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, uploaded.statusCode());

            var response = post(client, web.url() + "api/agent-ai?project=upload", "分析上传项目的启动方式");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("upload-entry"));
            assertTrue(response.body().contains("\"toolCalls\":1"));
        } finally { model.stop(0); }
    }

    @Test void persistsWebAgentMemoryAndCompletedCheckpointWhenStateIsConfigured() throws Exception {
        Path project = Files.createDirectory(workspace.resolve("stateful-project"));
        Files.writeString(project.resolve("README.md"), "demo", StandardCharsets.UTF_8);
        Path state = workspace.resolve("web-agent-state");
        var model = scriptedModel(List.of(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"state_1\",\"type\":\"function\",\"function\":{\"name\":\"health\",\"arguments\":\"{\\\"input\\\":\\\"\\\"}\"}}]}}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"项目状态正常。\"}}]}"));
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + model.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        try (var web = new LocalWebServer(project, 0, config, state); var client = HttpClient.newHttpClient()) {
            web.start();
            assertFalse(Files.exists(state), "启动服务不应提前创建状态目录");
            var response = post(client, web.url() + "api/agent-ai", "检查项目");
            assertEquals(200, response.statusCode());
            Path projectState = state.resolve("root");
            assertEquals(1, new AgentMemoryStore(project, projectState).readRecent(5).size());
            assertTrue(AgentCheckpointStore.inspect(projectState).completed());
            var health = client.send(HttpRequest.newBuilder(URI.create(web.url() + "api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue(health.body().contains("\"agentMemoryEnabled\":true"));
            var stateResponse = client.send(HttpRequest.newBuilder(URI.create(web.url() + "api/agent-state")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, stateResponse.statusCode());
            assertTrue(stateResponse.body().contains("\"enabled\":true"));
            assertTrue(stateResponse.body().contains("\"memoryEntries\":1"));
            assertTrue(stateResponse.body().contains("\"checkpoint\":\"COMPLETED\""));
            assertFalse(stateResponse.body().contains("检查项目"));
        } finally { model.stop(0); }
    }

    @Test void rejectsWebAgentStateInsideScannedWorkspace() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "demo", StandardCharsets.UTF_8);
        var config = new ModelConfig(URI.create("http://127.0.0.1:9/chat/completions"),
                "test", "", Duration.ofSeconds(1));
        assertTrue(assertThrows(java.io.IOException.class,
                () -> new LocalWebServer(workspace, 0, config, workspace.resolve("private-state")))
                .getMessage().contains("工作区之外"));
        assertFalse(Files.exists(workspace.resolve("private-state")));
    }

    private HttpResponse<String> post(HttpClient client, String endpoint, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(endpoint))
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpServer scriptedModel(List<String> responses) throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = responses.get(Math.min(count.getAndIncrement(), responses.size() - 1))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}

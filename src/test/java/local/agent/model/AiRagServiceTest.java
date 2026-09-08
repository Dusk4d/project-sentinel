package local.agent.model;

import com.sun.net.httpserver.HttpServer;
import local.agent.WorkspaceGuard;
import local.agent.rag.LocalRagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class AiRagServiceTest {
    @TempDir Path project;

    @Test void injectsRetrievedEvidenceAndUsesModelAnswer() throws Exception {
        Files.writeString(project.resolve("README.md"), "使用 start-web.cmd 启动，然后访问 8787 端口。", StandardCharsets.UTF_8);
        var prompt = new AtomicReference<String>();
        var server = server(200, "{\"choices\":[{\"message\":{\"content\":\"运行 start-web.cmd [README.md:1-1]\"}}]}", prompt);
        try {
            var result = service(server).ask("如何启动？");
            assertTrue(result.modelUsed());
            assertTrue(result.answer().contains("[README.md:1-1]"));
            assertTrue(prompt.get().contains("BEGIN EVIDENCE README.md:1-1"));
            assertTrue(prompt.get().contains("start-web.cmd"));
        } finally { server.stop(0); }
    }

    @Test void fallsBackToExtractiveAnswerWhenModelFails() throws Exception {
        Files.writeString(project.resolve("README.md"), "使用 start-web.cmd 启动。", StandardCharsets.UTF_8);
        var server = server(503, "unavailable", new AtomicReference<>());
        try {
            var result = service(server).ask("如何启动？");
            assertFalse(result.modelUsed());
            assertTrue(result.notice().contains("HTTP 503"));
            assertTrue(result.answer().contains("start-web.cmd"));
        } finally { server.stop(0); }
    }

    private AiRagService service(HttpServer server) throws Exception {
        var config = new ModelConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"),
                "test", "", Duration.ofSeconds(3));
        return new AiRagService(new LocalRagService(new WorkspaceGuard(project)), new OpenAiCompatibleClient(config));
    }

    private HttpServer server(int status, String body, AtomicReference<String> prompt) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            prompt.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}

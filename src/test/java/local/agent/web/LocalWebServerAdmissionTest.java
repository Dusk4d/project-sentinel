package local.agent.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class LocalWebServerAdmissionTest {
    @TempDir Path project;

    @Test void returnsRetryableBusyResponseWhileHealthRemainsAvailable() throws Exception {
        var gate = new ScanAdmissionGate();
        try (var held = gate.tryAcquire(); var server = new LocalWebServer(project, 0, gate);
             var client = HttpClient.newHttpClient()) {
            assertNotNull(held);
            server.start();

            var busy = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/analysis"))
                            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(429, busy.statusCode());
            assertEquals("1", busy.headers().firstValue("Retry-After").orElseThrow());
            assertTrue(busy.body().contains("scan already in progress"));

            var health = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertTrue(health.body().contains("\"scanBusy\":true"));
        }
    }
}

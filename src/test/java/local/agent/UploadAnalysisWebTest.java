package local.agent;

import local.agent.web.LocalWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class UploadAnalysisWebTest {
    @TempDir Path workspace;

    @Test void analyzesZipUploadAndLeavesNoTemporaryProject() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Host workspace");
        byte[] archive = archive("uploaded/README.md", "# Uploaded project documentation",
                "uploaded/src/main/java/App.java", "class App {}",
                "uploaded/src/test/java/AppTest.java", "class AppTest {}");
        try (var server = new LocalWebServer(workspace, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var request = HttpRequest.newBuilder(URI.create(server.url() + "api/upload-analysis"))
                    .header("Content-Type", "application/zip")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(archive)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"projectId\":\"upload\""));
            assertTrue(response.body().contains("\"temporary\":true"));
            assertTrue(response.body().contains("\"project\": \"uploaded\""));
            assertTrue(response.body().contains("\"sources\": 1"));
            assertTrue(response.body().contains("\"tests\": 1"));

            var rag = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/rag?project=upload"))
                            .header("Content-Type", "text/plain; charset=utf-8")
                            .POST(HttpRequest.BodyPublishers.ofString("Uploaded project documentation", StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, rag.statusCode());
            assertTrue(rag.body().contains("Uploaded project documentation"));

            var catalog = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/projects")).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue(catalog.body().contains("\"id\":\"upload\""));
            assertTrue(catalog.body().contains("\"temporary\":true"));
        }
        try (var children = Files.list(workspace)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith(".sentinel-upload-")));
        }
    }

    @Test void rejectsWrongMediaType() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Host workspace");
        try (var server = new LocalWebServer(workspace, 0); var client = HttpClient.newHttpClient()) {
            server.start();
            var response = client.send(HttpRequest.newBuilder(URI.create(server.url() + "api/upload-analysis"))
                            .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("x")).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(415, response.statusCode());
        }
    }

    private static byte[] archive(String... nameAndContent) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < nameAndContent.length; index += 2) {
                zip.putNextEntry(new ZipEntry(nameAndContent[index]));
                zip.write(nameAndContent[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

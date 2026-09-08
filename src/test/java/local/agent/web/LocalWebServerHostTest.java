package local.agent.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class LocalWebServerHostTest {
    @TempDir Path workspace;

    @Test void acceptsLoopbackHostsWithOptionalValidPorts() {
        assertTrue(LocalWebServer.allowedHost("127.0.0.1"));
        assertTrue(LocalWebServer.allowedHost("127.0.0.1:8787"));
        assertTrue(LocalWebServer.allowedHost("localhost:1"));
        assertTrue(LocalWebServer.allowedHost("LOCALHOST:65535"));
        assertTrue(LocalWebServer.allowedHost("[::1]"));
        assertTrue(LocalWebServer.allowedHost("[::1]:8787"));
    }

    @Test void rejectsMissingRemoteMalformedAndPrefixLookalikeHosts() {
        assertFalse(LocalWebServer.allowedHost(null));
        assertFalse(LocalWebServer.allowedHost(""));
        assertFalse(LocalWebServer.allowedHost("evil.example"));
        assertFalse(LocalWebServer.allowedHost("localhost.evil.example:8787"));
        assertFalse(LocalWebServer.allowedHost("127.0.0.1.evil.example"));
        assertFalse(LocalWebServer.allowedHost("localhost:0"));
        assertFalse(LocalWebServer.allowedHost("localhost:65536"));
        assertFalse(LocalWebServer.allowedHost("localhost:not-a-port"));
        assertFalse(LocalWebServer.allowedHost("[::1].evil.example"));
    }

    @Test void rejectsNonLoopbackHostBeforeDispatchingApiHandler() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Test workspace");
        try (var server = new LocalWebServer(workspace, 0)) {
            server.start();
            try (var socket = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
                socket.getOutputStream().write(("GET /api/health HTTP/1.1\r\n"
                        + "Host: attacker.example\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 403"), response);
                assertTrue(response.contains("\"error\":\"invalid host\""), response);
            }
        }
    }
}

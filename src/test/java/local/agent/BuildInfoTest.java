package local.agent;

import local.agent.cli.CommandLine;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

final class BuildInfoTest {
    @Test void runtimeVersionComesFromFilteredMavenResource() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        var matcher = Pattern.compile("<version>([^<]+)</version>").matcher(pom);
        assertTrue(matcher.find());
        assertEquals(matcher.group(1), BuildInfo.version());
        assertEquals(BuildInfo.version(), CommandLine.VERSION);
        assertFalse(BuildInfo.version().contains("${"));
        assertTrue(pom.contains("<propertiesEncoding>UTF-8</propertiesEncoding>"));
        assertTrue(pom.contains("<project.build.outputTimestamp>"));
    }
}

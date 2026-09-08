package local.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class DistributionContractTest {
    @Test void assemblyIncludesExecutableJarLaunchersAndDocumentation() throws Exception {
        String pom = read("pom.xml");
        String assembly = read("src/assembly/distribution.xml");
        assertTrue(pom.contains("<artifactId>maven-assembly-plugin</artifactId>"));
        assertTrue(pom.contains("<version>3.8.0</version>"));
        assertTrue(assembly.contains("<outputFileNameMapping>workspace-agent.jar</outputFileNameMapping>"));
        assertTrue(assembly.contains("<include>README.md</include>"));
        assertTrue(assembly.contains("<include>LICENSE</include>"));
        assertTrue(assembly.contains("${project.basedir}/src/distribution"));
    }

    @Test void binaryLaunchersRunTheBundledJarWithoutMaven() throws Exception {
        String windows = read("src/distribution/project-sentinel.cmd");
        String unix = read("src/distribution/project-sentinel.sh");
        String windowsWeb = read("src/distribution/start-web.cmd");
        String unixWeb = read("src/distribution/start-web.sh");
        assertTrue(windows.contains("workspace-agent.jar"));
        assertTrue(unix.contains("workspace-agent.jar"));
        assertFalse(windows.contains("mvnw"));
        assertFalse(unix.contains("mvnw"));
        assertTrue(windowsWeb.contains("--serve"));
        assertTrue(unixWeb.contains("--serve"));
    }

    @Test void ciUploadsChecksummedArtifactAndTagWorkflowGuardsVersion() throws Exception {
        String ci = read(".github/workflows/ci.yml");
        String release = read(".github/workflows/release.yml");
        assertTrue(ci.contains("actions/upload-artifact@v7"));
        assertTrue(ci.contains("sha256sum"));
        assertTrue(ci.contains("if-no-files-found: error"));
        assertTrue(release.contains("tags:"));
        assertTrue(release.contains("test \"v$version\" = \"$GITHUB_REF_NAME\""));
        assertTrue(release.contains("gh release create"));
        assertTrue(release.contains("--verify-tag"));
    }

    private String read(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }
}

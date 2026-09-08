package local.agent;

import local.agent.analysis.ProjectDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class ProjectDiscoveryTest {
    @TempDir Path root;

    @Test void findsNestedProjectsAndSkipsGeneratedTrees() throws Exception {
        Path nested = root.resolve("semester/course/assignment");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("pom.xml"), "<project/>");
        Path dependency = root.resolve("node_modules/vendor");
        Files.createDirectories(dependency);
        Files.writeString(dependency.resolve("package.json"), "{}");
        Path state = root.resolve("agent-state/copied-project");
        Files.createDirectories(state);
        Files.writeString(state.resolve("go.mod"), "module ignored");
        var projects = new ProjectDiscovery().discover(root);
        assertEquals(1, projects.size());
        assertEquals(nested.toRealPath(), projects.get(0));
    }

    @Test void honorsBoundedDepthAndRejectsUnsafeDepth() throws Exception {
        Path deep = root.resolve("a/b/c/d/e");
        Files.createDirectories(deep);
        Files.writeString(deep.resolve("Cargo.toml"), "[package]");
        assertTrue(new ProjectDiscovery().discover(root).isEmpty());
        assertEquals(1, new ProjectDiscovery().discover(root, 6).size());
        assertThrows(IllegalArgumentException.class, () -> new ProjectDiscovery().discover(root, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProjectDiscovery().discover(root, 13));
    }

    @Test void provesTruncationWithOneAdditionalProjectAndStopsAtTheBound() throws Exception {
        for (int i = 0; i < 4; i++) {
            Path project = Files.createDirectory(root.resolve("project-" + i));
            Files.writeString(project.resolve("pom.xml"), "<project/>");
        }
        var exact = new ProjectDiscovery().discoverBounded(root, 2, 4);
        assertEquals(4, exact.projects().size());
        assertFalse(exact.truncated());

        Path extra = Files.createDirectory(root.resolve("project-extra"));
        Files.writeString(extra.resolve("pom.xml"), "<project/>");
        var truncated = new ProjectDiscovery().discoverBounded(root, 2, 4);
        assertEquals(4, truncated.projects().size());
        assertTrue(truncated.truncated());
        assertEquals(4, truncated.maximumProjects());
        assertThrows(IllegalArgumentException.class, () -> new ProjectDiscovery().discoverBounded(root, 2, 0));
    }
}

package local.agent;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class IdeaRunConfigurationTest {
    @Test void sharedWebConfigurationIsValidAndContainsNoSecret() throws Exception {
        Path configuration = Path.of(".run", "Project Sentinel Web.run.xml");
        assertTrue(Files.isRegularFile(configuration));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(configuration.toFile());
        assertEquals("component", document.getDocumentElement().getTagName());

        String xml = Files.readString(configuration);
        assertTrue(xml.contains("local.agent.Main"));
        assertTrue(xml.contains("SENTINEL_MODEL_BASE_URL"));
        assertTrue(xml.contains("SENTINEL_MODEL_NAME"));
        assertTrue(xml.contains("qwen3:1.7b"));
        assertFalse(xml.contains("SENTINEL_MODEL_API_KEY"));
    }
}

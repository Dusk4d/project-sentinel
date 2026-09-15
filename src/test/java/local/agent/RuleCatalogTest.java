package local.agent;

import local.agent.analysis.RuleCatalog;
import local.agent.report.RuleCatalogWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class RuleCatalogTest {
    @Test void definitionsHaveUniqueKnownIdsAndRenderableHelp() {
        assertEquals(RuleCatalog.DEFINITIONS.size(), RuleCatalog.KNOWN_IDS.size());
        assertTrue(RuleCatalog.KNOWN_IDS.contains(RuleCatalog.DOCS_README_UNVERIFIED));
        assertTrue(RuleCatalog.KNOWN_IDS.contains(RuleCatalog.BUILD_MANIFEST_UNVERIFIED));
        String output = new RuleCatalogWriter().render();
        for (var rule : RuleCatalog.DEFINITIONS) {
            assertTrue(output.contains(rule.id()));
            assertFalse(rule.category().isBlank());
            assertFalse(rule.trigger().isBlank());
        }
    }
}

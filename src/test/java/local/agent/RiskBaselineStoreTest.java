package local.agent;

import local.agent.daily.RiskBaseline;
import local.agent.daily.RiskBaselineStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class RiskBaselineStoreTest {
    @TempDir Path temp;

    @Test void atomicallyRoundTripsVersionedBaseline() throws Exception {
        Path file = temp.resolve("state").resolve(RiskBaselineStore.FILE_NAME);
        var expected = new RiskBaseline("C:\\项目 路径", Set.of("build.manifest", "tests.missing"));
        var store = new RiskBaselineStore();
        store.save(file, expected);
        assertEquals(expected, store.read(file).orElseThrow());
    }

    @Test void rejectsCorruptOrUnknownBaselineContent() throws Exception {
        Path file = temp.resolve("risk.properties");
        Files.writeString(file, "schemaVersion=2\nprojectRootBase64=eA\nhighRiskRuleIds=unknown.rule\n");
        assertThrows(IOException.class, () -> new RiskBaselineStore().read(file));
    }
}

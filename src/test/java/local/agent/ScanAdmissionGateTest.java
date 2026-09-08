package local.agent;

import local.agent.web.ScanAdmissionGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ScanAdmissionGateTest {
    @Test void admitsOneScanAndReleasesExactlyOnce() {
        var gate = new ScanAdmissionGate();
        var first = gate.tryAcquire();
        assertNotNull(first);
        assertTrue(gate.busy());
        assertNull(gate.tryAcquire());

        first.close();
        first.close();
        assertFalse(gate.busy());
        try (var next = gate.tryAcquire()) {
            assertNotNull(next);
            assertTrue(gate.busy());
        }
        assertFalse(gate.busy());
    }
}

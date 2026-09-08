package local.agent.web;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanAdmissionGate {
    private final Semaphore permit = new Semaphore(1, true);

    public Lease tryAcquire() {
        return permit.tryAcquire() ? new Lease() : null;
    }

    public boolean busy() { return permit.availablePermits() == 0; }

    public final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease() { }
        @Override public void close() {
            if (closed.compareAndSet(false, true)) permit.release();
        }
    }
}

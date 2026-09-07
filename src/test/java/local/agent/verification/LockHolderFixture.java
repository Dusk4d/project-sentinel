package local.agent.verification;

import local.agent.state.StateRunLock;

import java.nio.file.Path;

public final class LockHolderFixture {
    private LockHolderFixture() { }

    public static void main(String[] args) throws Exception {
        try (var ignored = StateRunLock.acquire(Path.of(args[0]))) {
            System.out.println("LOCKED");
            System.out.flush();
            Thread.sleep(Long.parseLong(args[1]));
        }
    }
}

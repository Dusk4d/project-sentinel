package local.agent.state;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import local.agent.report.AtomicTextStore;

public final class StateRunLock implements AutoCloseable {
    static final String LOCK_NAME = ".workspace-agent.lock";
    static final String INFO_NAME = ".workspace-agent.lock.info";
    private final FileChannel channel;
    private final FileLock lock;

    private StateRunLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static StateRunLock acquire(Path stateDirectory) throws IOException {
        return acquire(stateDirectory, "stateful-run");
    }

    public static StateRunLock acquire(Path stateDirectory, String operation) throws IOException {
        Path state = stateDirectory.toAbsolutePath().normalize();
        Files.createDirectories(state);
        Path lockFile = state.resolve(LOCK_NAME);
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                lock = null;
            }
            if (lock == null) {
                channel.close();
                throw new RunAlreadyActiveException(lockFile);
            }
            String safeOperation = operation.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
            String metadata = "pid=" + ProcessHandle.current().pid() + "\nstartedAt=" + Instant.now()
                    + "\noperation=" + safeOperation + "\n";
            new AtomicTextStore().write(state.resolve(INFO_NAME), metadata);
            return new StateRunLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            if (channel.isOpen()) channel.close();
            throw failure;
        }
    }

    @Override public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}

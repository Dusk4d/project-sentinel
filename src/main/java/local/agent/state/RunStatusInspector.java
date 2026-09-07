package local.agent.state;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class RunStatusInspector {
    public RunStatus inspect(Path stateDirectory) throws IOException {
        Path state = stateDirectory.toAbsolutePath().normalize();
        Path lockFile = state.resolve(StateRunLock.LOCK_NAME);
        if (!Files.isRegularFile(lockFile)) return new RunStatus(false, readMetadata(state));
        boolean active;
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
            FileLock probe = null;
            try {
                probe = channel.tryLock();
                active = probe == null;
            } catch (OverlappingFileLockException busy) {
                active = true;
            } finally {
                if (probe != null) probe.release();
            }
        }
        RunMetadata metadata = readMetadata(state);
        for (int attempt = 0; active && metadata == null && attempt < 5; attempt++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            metadata = readMetadata(state);
        }
        return new RunStatus(active, metadata);
    }

    private RunMetadata readMetadata(Path state) throws IOException {
        Path info = state.resolve(StateRunLock.INFO_NAME);
        if (!Files.isRegularFile(info)) return null;
        long pid = -1;
        Instant startedAt = null;
        String operation = "unknown";
        for (String line : Files.readAllLines(info, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator < 1) continue;
            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            try {
                if (key.equals("pid")) pid = Long.parseLong(value);
                if (key.equals("startedAt")) startedAt = Instant.parse(value);
            } catch (RuntimeException ignored) { }
            if (key.equals("operation") && !value.isBlank()) operation = value;
        }
        return new RunMetadata(pid, startedAt, operation);
    }
}

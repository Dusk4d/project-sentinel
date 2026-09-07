package local.agent.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicTextStore {
    public Path write(Path outputFile, String content) throws IOException {
        Path target = outputFile.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("输出文件必须有父目录");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".atomic-text-", ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
}

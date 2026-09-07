package local.agent.state;

import java.io.IOException;
import java.nio.file.Path;

public final class RunAlreadyActiveException extends IOException {
    public RunAlreadyActiveException(Path lockFile) {
        super("状态目录已有任务运行: " + lockFile.toAbsolutePath().normalize());
    }
}

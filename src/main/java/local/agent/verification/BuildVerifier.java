package local.agent.verification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;

public final class BuildVerifier {
    private static final int MAX_OUTPUT_CHARS = 64 * 1024;
    private final BuildCommandProvider commands;

    public BuildVerifier() { this(new BuildCommandDetector()); }
    public BuildVerifier(BuildCommandProvider commands) { this.commands = commands; }

    public BuildVerification verify(Path project, Duration timeout) throws IOException, InterruptedException {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(30)) > 0)
            throw new IllegalArgumentException("构建超时必须大于 0 且不超过 30 分钟");
        Path root = project.toRealPath();
        List<String> command = commands.detect(root);
        if (command.isEmpty()) return new BuildVerification(BuildVerification.Status.UNSUPPORTED, command, -1, Duration.ZERO, "未识别可执行构建命令");
        Instant started = Instant.now();
        Process process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var outputFuture = executor.submit(() -> readBounded(process));
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                return new BuildVerification(BuildVerification.Status.TIMED_OUT, command, -1,
                        Duration.between(started, Instant.now()), outputFuture.get(3, TimeUnit.SECONDS));
            }
            int code = process.exitValue();
            return new BuildVerification(code == 0 ? BuildVerification.Status.PASSED : BuildVerification.Status.FAILED,
                    command, code, Duration.between(started, Instant.now()), outputFuture.get(3, TimeUnit.SECONDS));
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("无法收集构建输出", e);
        }
    }

    private String readBounded(Process process) throws IOException {
        var captured = new ByteArrayOutputStream(MAX_OUTPUT_CHARS);
        boolean truncated = false;
        byte[] buffer = new byte[8192];
        try (var input = process.getInputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_CHARS - captured.size();
                if (remaining > 0) captured.write(buffer, 0, Math.min(read, remaining));
                if (read > remaining) truncated = true;
            }
        }
        String text = captured.toString(StandardCharsets.UTF_8);
        return truncated ? text + "\n[输出已截断]" : text;
    }
}

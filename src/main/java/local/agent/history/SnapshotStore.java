package local.agent.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotStore {
    private static final String HEADER_LINE = "timestamp\tproject\tscore\tfiles\tsources\ttests\ttodos";
    private static final String HEADER = HEADER_LINE + "\n";

    public void append(Path file, HealthSnapshot snapshot) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        validateSnapshot(snapshot, "待写入快照");
        var previous = Files.exists(target) ? read(target) : List.<HealthSnapshot>of();
        if (!previous.isEmpty()) {
            HealthSnapshot latest = previous.get(previous.size() - 1);
            if (!latest.project().equals(sanitize(snapshot.project())))
                throw new IOException("历史文件属于项目 " + latest.project() + "，不能写入 " + sanitize(snapshot.project()));
            if (snapshot.timestamp().isBefore(latest.timestamp()))
                throw new IOException("快照时间早于历史中最新记录");
        }
        String existing = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : HEADER;
        String row = String.join("\t", snapshot.timestamp().toString(), sanitize(snapshot.project()),
                Integer.toString(snapshot.score()), Integer.toString(snapshot.files()), Integer.toString(snapshot.sources()),
                Integer.toString(snapshot.tests()), Integer.toString(snapshot.todos())) + "\n";
        Path temp = Files.createTempFile(target.getParent(), ".history-", ".tmp");
        try {
            Files.writeString(temp, existing + row, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public List<HealthSnapshot> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        var result = new ArrayList<HealthSnapshot>();
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER_LINE))
            throw new IOException("历史文件表头无效: " + file.toAbsolutePath().normalize());
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] p = lines.get(i).split("\\t", -1);
            if (p.length != 7) throw new IOException("历史文件第 " + (i + 1) + " 行格式无效");
            try {
                var snapshot = new HealthSnapshot(Instant.parse(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]));
                validateSnapshot(snapshot, "历史文件第 " + (i + 1) + " 行");
                if (!result.isEmpty() && snapshot.timestamp().isBefore(result.get(result.size() - 1).timestamp()))
                    throw new IOException("历史文件第 " + (i + 1) + " 行时间早于前一行");
                if (!result.isEmpty() && !snapshot.project().equals(result.get(0).project()))
                    throw new IOException("历史文件第 " + (i + 1) + " 行混入不同项目");
                result.add(snapshot);
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IOException("历史文件第 " + (i + 1) + " 行数据无效", e);
            }
        }
        return List.copyOf(result);
    }

    private String sanitize(String value) { return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }

    private void validateSnapshot(HealthSnapshot snapshot, String context) throws IOException {
        if (snapshot.timestamp() == null) throw new IOException(context + " 缺少时间戳");
        if (snapshot.project() == null || snapshot.project().isBlank()) throw new IOException(context + " 缺少项目名");
        if (snapshot.score() < 0 || snapshot.score() > 100) throw new IOException(context + " 健康分必须在 0 到 100 之间");
        if (snapshot.files() < 0 || snapshot.sources() < 0 || snapshot.tests() < 0 || snapshot.todos() < 0)
            throw new IOException(context + " 计数不能为负数");
    }
}

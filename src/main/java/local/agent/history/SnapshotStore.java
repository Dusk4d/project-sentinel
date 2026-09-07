package local.agent.history;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SnapshotStore {
    private static final String HEADER = "timestamp\tproject\tscore\tfiles\tsources\ttests\ttodos\n";

    public void append(Path file, HealthSnapshot snapshot) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        String existing = Files.exists(target) ? Files.readString(target, StandardCharsets.UTF_8) : HEADER;
        if (!existing.startsWith(HEADER)) throw new IOException("历史文件格式无效: " + target);
        String row = String.join("\t", snapshot.timestamp().toString(), sanitize(snapshot.project()),
                Integer.toString(snapshot.score()), Integer.toString(snapshot.files()), Integer.toString(snapshot.sources()),
                Integer.toString(snapshot.tests()), Integer.toString(snapshot.todos())) + "\n";
        Path temp = Files.createTempFile(target.getParent(), ".history-", ".tmp");
        try {
            Files.writeString(temp, existing + row, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    public List<HealthSnapshot> read(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        var result = new ArrayList<HealthSnapshot>();
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] p = lines.get(i).split("\\t", -1);
            if (p.length != 7) throw new IOException("历史文件第 " + (i + 1) + " 行格式无效");
            result.add(new HealthSnapshot(Instant.parse(p[0]), p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6])));
        }
        return List.copyOf(result);
    }

    private String sanitize(String value) { return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }
}

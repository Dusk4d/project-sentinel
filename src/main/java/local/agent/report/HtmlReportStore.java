package local.agent.report;

import local.agent.analysis.ProjectProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HtmlReportStore {
    public Path save(ProjectProfile profile, Path outputFile) throws IOException {
        Path target = outputFile.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) throw new IOException("HTML 输出文件必须有父目录");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".html-report-", ".tmp");
        try {
            Files.writeString(temporary, new HtmlReportWriter().render(profile), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            return target;
        } finally { Files.deleteIfExists(temporary); }
    }
}

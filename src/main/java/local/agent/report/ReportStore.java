package local.agent.report;

import local.agent.analysis.ProjectProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class ReportStore {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public Path save(ProjectProfile profile, Path outputDirectory) throws IOException {
        return save(profile, outputDirectory, LocalDateTime.now());
    }

    public Path save(ProjectProfile profile, Path outputDirectory, LocalDateTime observedAt) throws IOException {
        Path dir = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String safeName = profile.name().replaceAll("[^a-zA-Z0-9._-]", "_");
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Path destination = dir.resolve(FILE_TIME.format(observedAt) + "-" + safeName + "-" + unique + ".md");
        Path temporary = Files.createTempFile(dir, ".report-", ".tmp");
        try {
            Files.writeString(temporary, new MarkdownReportWriter().render(profile), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupportedAtomicMove) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return destination;
    }
}

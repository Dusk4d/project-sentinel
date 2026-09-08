package local.agent.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ZipProjectUpload implements AutoCloseable {
    static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final int MAX_ENTRIES = 5_000;
    private final Path temporaryDirectory;
    private final Path projectRoot;

    private ZipProjectUpload(Path temporaryDirectory, Path projectRoot) {
        this.temporaryDirectory = temporaryDirectory;
        this.projectRoot = projectRoot;
    }

    static ZipProjectUpload extract(InputStream input, Path workspace, long declaredLength) throws IOException {
        if (declaredLength > MAX_ARCHIVE_BYTES) throw new UploadRejectedException("ZIP 超过 20 MiB 限制");
        Path temporary = Files.createTempDirectory(workspace, ".sentinel-upload-");
        try {
            Path extracted = Files.createDirectory(temporary.resolve("project"));
            unpack(input, extracted);
            return new ZipProjectUpload(temporary, collapseSingleTopLevelDirectory(extracted));
        } catch (IOException failure) {
            deleteTree(temporary);
            throw failure;
        }
    }

    Path projectRoot() { return projectRoot; }

    private static void unpack(InputStream raw, Path target) throws IOException {
        long expandedBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[16 * 1024];
        try (var bounded = new BoundedInputStream(raw); var zip = new ZipInputStream(bounded)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new UploadRejectedException("ZIP 文件条目超过 5000 个限制");
                Path output = safeOutputPath(target, entry.getName());
                if (entry.isDirectory()) { Files.createDirectories(output); continue; }
                Files.createDirectories(output.getParent());
                long fileBytes = 0;
                try (var out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) continue;
                        fileBytes += read;
                        expandedBytes += read;
                        if (fileBytes > MAX_FILE_BYTES) throw new UploadRejectedException("ZIP 中单个文件超过 10 MiB 限制");
                        if (expandedBytes > MAX_EXPANDED_BYTES) throw new UploadRejectedException("ZIP 解压内容超过 100 MiB 限制");
                        out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            if (entries == 0) throw new UploadRejectedException("ZIP 为空或格式无效");
        }
    }

    private static Path safeOutputPath(Path target, String name) throws UploadRejectedException {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) throw new UploadRejectedException("ZIP 包含无效路径");
        Path output = target.resolve(name.replace('\\', '/')).normalize();
        if (!output.startsWith(target)) throw new UploadRejectedException("ZIP 包含越界路径");
        return output;
    }

    private static Path collapseSingleTopLevelDirectory(Path root) throws IOException {
        try (var children = Files.list(root)) {
            var items = children.toList();
            return items.size() == 1 && Files.isDirectory(items.get(0)) ? items.get(0) : root;
        }
    }

    @Override public void close() throws IOException { deleteTree(temporaryDirectory); }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    static final class UploadRejectedException extends IOException {
        UploadRejectedException(String message) { super(message); }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long count;
        BoundedInputStream(InputStream delegate) { this.delegate = delegate; }
        @Override public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0 && ++count > MAX_ARCHIVE_BYTES) throw new UploadRejectedException("ZIP 超过 20 MiB 限制");
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0 && (count += read) > MAX_ARCHIVE_BYTES) throw new UploadRejectedException("ZIP 超过 20 MiB 限制");
            return read;
        }
    }
}

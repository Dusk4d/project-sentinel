package local.agent.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class ZipProjectUploadTest {
    @TempDir Path workspace;

    @Test void extractsSingleProjectDirectoryAndRemovesTemporaryFiles() throws Exception {
        byte[] zip = archive("demo/README.md", "# Demo", "demo/src/App.java", "class App {}");
        Path temporary;
        try (var upload = ZipProjectUpload.extract(new ByteArrayInputStream(zip), workspace, zip.length)) {
            temporary = upload.projectRoot().getParent().getParent();
            assertEquals("demo", upload.projectRoot().getFileName().toString());
            assertEquals("# Demo", Files.readString(upload.projectRoot().resolve("README.md")));
        }
        assertFalse(Files.exists(temporary));
    }

    @Test void rejectsZipSlipAndCleansTemporaryDirectory() throws Exception {
        byte[] zip = archive("../outside.txt", "unsafe");
        var failure = assertThrows(ZipProjectUpload.UploadRejectedException.class,
                () -> ZipProjectUpload.extract(new ByteArrayInputStream(zip), workspace, zip.length));
        assertTrue(failure.getMessage().contains("越界"));
        try (var children = Files.list(workspace)) { assertEquals(0, children.count()); }
    }

    @Test void rejectsDeclaredOversizeBeforeCreatingTemporaryDirectory() throws Exception {
        var failure = assertThrows(ZipProjectUpload.UploadRejectedException.class,
                () -> ZipProjectUpload.extract(new ByteArrayInputStream(new byte[0]), workspace,
                        ZipProjectUpload.MAX_ARCHIVE_BYTES + 1));
        assertTrue(failure.getMessage().contains("20 MiB"));
        try (var children = Files.list(workspace)) { assertEquals(0, children.count()); }
    }

    @Test void extractsLegacyGbkEntryNamesWhenUtf8DecodingFails() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes, Charset.forName("GBK"))) {
            zip.putNextEntry(new ZipEntry("文档/说明.md"));
            zip.write("中文内容".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (var upload = ZipProjectUpload.extract(new ByteArrayInputStream(bytes.toByteArray()), workspace, bytes.size())) {
            assertEquals("文档", upload.projectRoot().getFileName().toString());
            assertEquals("中文内容", Files.readString(upload.projectRoot().resolve("说明.md")));
        }
    }

    private static byte[] archive(String... nameAndContent) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (int index = 0; index < nameAndContent.length; index += 2) {
                zip.putNextEntry(new ZipEntry(nameAndContent[index]));
                zip.write(nameAndContent[index + 1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

package local.agent.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ArtifactSignerTest {
    @TempDir Path root;

    @Test void generatesRotatableKeysSignsAndVerifiesWithoutNetwork() throws Exception {
        var service = new ArtifactSigner();
        var first = service.generate(root.resolve("keys"), "2026-09-a");
        var second = service.generate(root.resolve("keys"), "2026-10-b");
        assertNotEquals(Files.readAllBytes(first.publicKey()), Files.readAllBytes(second.publicKey()));
        Path artifact = Files.writeString(root.resolve("release.zip"), "release bytes");
        Path signature = service.sign(artifact, first.privateKey(), first.keyId(), root.resolve("release.zip.sig"));

        var verified = service.verify(artifact, first.publicKey(), signature);
        assertTrue(verified.valid());
        assertEquals("2026-09-a", verified.keyId());
        assertEquals(64, verified.artifactSha256().length());
        assertTrue(Files.readString(signature).contains("algorithm=Ed25519"));
        assertTrue(Files.readString(signature).contains("schemaVersion=2"));
    }

    @Test void detectsTamperingAndWrongPublicKey() throws Exception {
        var service = new ArtifactSigner();
        var first = service.generate(root.resolve("keys"), "first");
        var second = service.generate(root.resolve("keys"), "second");
        Path artifact = Files.writeString(root.resolve("artifact.jar"), "original");
        Path signature = service.sign(artifact, first.privateKey(), first.keyId(), root.resolve("artifact.jar.sig"));
        Files.writeString(artifact, "tampered");
        assertFalse(service.verify(artifact, first.publicKey(), signature).valid());

        Files.writeString(artifact, "original");
        assertFalse(service.verify(artifact, second.publicKey(), signature).valid());
    }

    @Test void neverOverwritesKeysOrSignaturesAndRejectsUnsafeKeyIds() throws Exception {
        var service = new ArtifactSigner();
        var keys = service.generate(root.resolve("keys"), "stable");
        assertThrows(java.io.IOException.class, () -> service.generate(root.resolve("keys"), "stable"));
        assertThrows(IllegalArgumentException.class, () -> service.generate(root.resolve("keys"), "../escape"));
        Path artifact = Files.writeString(root.resolve("artifact.zip"), "content");
        Path output = root.resolve("artifact.zip.sig");
        service.sign(artifact, keys.privateKey(), keys.keyId(), output);
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> service.sign(artifact, keys.privateKey(), keys.keyId(), output));
    }

    @Test void bindsKeyIdAndRejectsLegacyUnsignedMetadata() throws Exception {
        var service = new ArtifactSigner();
        var keys = service.generate(root.resolve("keys"), "first");
        Path artifact = Files.writeString(root.resolve("artifact.jar"), "original");
        Path signature = service.sign(artifact, keys.privateKey(), keys.keyId(), root.resolve("artifact.jar.sig"));
        String metadata = Files.readString(signature);

        Files.writeString(signature, metadata.replace("keyId=first", "keyId=other"));
        assertFalse(service.verify(artifact, keys.publicKey(), signature).valid());

        Files.writeString(signature, metadata.replace("schemaVersion=2", "schemaVersion=1"));
        assertThrows(java.io.IOException.class, () -> service.verify(artifact, keys.publicKey(), signature));
    }
}

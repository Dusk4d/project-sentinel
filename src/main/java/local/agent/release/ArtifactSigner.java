package local.agent.release;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.Set;

public final class ArtifactSigner {
    private static final long MAX_METADATA_BYTES = 64 * 1024;

    public KeyFiles generate(Path directory, String keyId) throws Exception {
        requireKeyId(keyId);
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path privateKey = root.resolve("private-" + keyId + ".pk8");
        Path publicKey = root.resolve("public-" + keyId + ".x509");
        if (Files.exists(privateKey) || Files.exists(publicKey)) throw new IOException("签名密钥已存在，拒绝覆盖: " + keyId);
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        boolean privateCreated = false;
        try {
            Files.write(privateKey, pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
            privateCreated = true;
            restrictPrivateKey(privateKey);
            Files.write(publicKey, pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) {
            if (privateCreated) Files.deleteIfExists(privateKey);
            throw failure;
        }
        return new KeyFiles(privateKey, publicKey, keyId);
    }

    public Path sign(Path artifact, Path privateKeyFile, String keyId, Path output) throws Exception {
        requireKeyId(keyId);
        Path input = regular(artifact, "构件");
        byte[] key = boundedRead(privateKeyFile, 16 * 1024, "私钥");
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(key)));
        update(signer, input);
        String metadata = "schemaVersion=1\nalgorithm=Ed25519\nkeyId=" + keyId + "\nartifactSha256="
                + sha256(input) + "\nsignature=" + Base64.getEncoder().encodeToString(signer.sign()) + "\n";
        Path target = output.toAbsolutePath().normalize();
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.writeString(target, metadata, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
        return target;
    }

    public Verification verify(Path artifact, Path publicKeyFile, Path signatureFile) throws Exception {
        Path input = regular(artifact, "构件");
        byte[] publicBytes = boundedRead(publicKeyFile, 16 * 1024, "公钥");
        var properties = new Properties();
        properties.load(new StringReader(new String(boundedRead(signatureFile, MAX_METADATA_BYTES, "签名文件"), StandardCharsets.US_ASCII)));
        if (!"1".equals(properties.getProperty("schemaVersion")) || !"Ed25519".equals(properties.getProperty("algorithm")))
            throw new IOException("不支持的签名文件版本或算法");
        String keyId = required(properties, "keyId");
        String actualDigest = sha256(input);
        String expectedDigest = required(properties, "artifactSha256");
        if (!MessageDigest.isEqual(expectedDigest.getBytes(StandardCharsets.US_ASCII), actualDigest.getBytes(StandardCharsets.US_ASCII)))
            return new Verification(false, keyId, actualDigest, "构件 SHA-256 与签名记录不一致");
        byte[] signed;
        try { signed = Base64.getDecoder().decode(required(properties, "signature")); }
        catch (IllegalArgumentException invalid) { throw new IOException("签名值不是有效 Base64", invalid); }
        var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicBytes)));
        update(verifier, input);
        boolean valid = verifier.verify(signed);
        return new Verification(valid, keyId, actualDigest, valid ? "签名有效" : "Ed25519 签名无效");
    }

    private static void update(Signature signature, Path file) throws Exception {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) signature.update(buffer, 0, read);
        }
    }

    private static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path regular(Path file, String label) throws IOException {
        Path real = file.toRealPath();
        if (!Files.isRegularFile(real)) throw new IOException(label + "不是常规文件: " + real);
        return real;
    }

    private static byte[] boundedRead(Path file, long maximum, String label) throws IOException {
        Path real = regular(file, label);
        if (Files.size(real) > maximum) throw new IOException(label + "超过安全大小限制");
        return Files.readAllBytes(real);
    }

    private static String required(Properties properties, String name) throws IOException {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank()) throw new IOException("签名文件缺少 " + name);
        return value.strip();
    }

    private static void requireKeyId(String keyId) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("keyId 必须为 1-64 位字母、数字、点、下划线或连字符");
    }

    private static void restrictPrivateKey(Path path) {
        try { Files.setPosixFilePermissions(path, Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)); }
        catch (UnsupportedOperationException | IOException ignored) { }
    }

    public record KeyFiles(Path privateKey, Path publicKey, String keyId) { }
    public record Verification(boolean valid, String keyId, String artifactSha256, String message) { }
}

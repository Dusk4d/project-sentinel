package local.agent.rag;

import local.agent.WorkspaceGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LocalRagService {
    private static final int MAX_FILES = 1_000;
    private static final int MAX_CHUNKS = 5_000;
    private static final long MAX_FILE_BYTES = 128 * 1024;
    private static final int CHUNK_LINES = 10;
    private static final int CHUNK_STEP = 8;
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(".git", "target", "node_modules", "build", "dist", "coverage", ".idea", ".gradle", "venv", ".venv");
    private static final Set<String> TEXT_NAMES = Set.of("readme", "license", "dockerfile", "makefile", "pom.xml", "build.gradle", "settings.gradle", "package.json", "pyproject.toml", "cargo.toml", "go.mod");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(".md", ".txt", ".adoc", ".rst", ".java", ".kt", ".py", ".js", ".jsx", ".ts", ".tsx", ".vue", ".svelte", ".go", ".rs", ".c", ".cpp", ".h", ".cs", ".rb", ".php", ".xml", ".json", ".yml", ".yaml", ".toml", ".properties", ".gradle", ".sh", ".ps1");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]+|[\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);
    private final WorkspaceGuard guard;

    public LocalRagService(WorkspaceGuard guard) { this.guard = guard; }

    public RagAnswer ask(String question) throws IOException {
        String query = question == null ? "" : question.strip();
        if (query.isEmpty()) throw new IllegalArgumentException("RAG 问题不能为空");
        List<Chunk> chunks = index();
        List<String> queryTerms = tokens(query).stream().distinct().toList();
        if (queryTerms.isEmpty() || chunks.isEmpty()) return noEvidence(query);

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Chunk chunk : chunks) {
            for (String term : new HashSet<>(chunk.terms())) documentFrequency.merge(term, 1, Integer::sum);
        }
        double averageLength = chunks.stream().mapToInt(chunk -> chunk.terms().size()).average().orElse(1.0);
        List<RagHit> hits = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, queryTerms, documentFrequency, chunks.size(), averageLength)))
                .filter(scored -> scored.score() > 0.0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(scored -> scored.chunk().path()).thenComparingInt(scored -> scored.chunk().startLine()))
                .limit(5)
                .map(scored -> new RagHit(scored.chunk().path(), scored.chunk().startLine(), scored.chunk().endLine(), scored.score(), scored.chunk().text()))
                .toList();
        if (hits.isEmpty()) return noEvidence(query);
        String extract = hits.get(0).text().replaceAll("\\s+", " ").strip();
        if (extract.length() > 360) extract = extract.substring(0, 357) + "...";
        return new RagAnswer(1, query, "最相关的本地证据是：" + extract + "（请结合下方来源核验。）", hits);
    }

    private RagAnswer noEvidence(String query) {
        return new RagAnswer(1, query, "没有找到足以支持回答的本地证据。", List.of());
    }

    private List<Chunk> index() throws IOException {
        var chunks = new ArrayList<Chunk>();
        int[] files = {0};
        Files.walkFileTree(guard.root(), new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(guard.root()) && IGNORED_DIRECTORIES.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT)))
                    return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (files[0] >= MAX_FILES || chunks.size() >= MAX_CHUNKS) return FileVisitResult.TERMINATE;
                if (!safeTextFile(file)) return FileVisitResult.CONTINUE;
                files[0]++;
                addChunks(file, chunks);
                return chunks.size() >= MAX_CHUNKS ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
        });
        return List.copyOf(chunks);
    }

    private boolean safeTextFile(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES || !file.toRealPath().startsWith(guard.root())) return false;
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.equals(".env") || name.startsWith(".env.") || name.contains("credential") || name.endsWith(".pem") || name.endsWith(".p12") || name.endsWith(".pfx")) return false;
            return TEXT_NAMES.contains(name) || TEXT_NAMES.stream().anyMatch(base -> name.startsWith(base + "."))
                    || TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
        } catch (IOException ignored) { return false; }
    }

    private void addChunks(Path file, List<Chunk> chunks) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = guard.root().relativize(file.toRealPath()).toString().replace('\\', '/');
            for (int start = 0; start < lines.size() && chunks.size() < MAX_CHUNKS; start += CHUNK_STEP) {
                int end = Math.min(lines.size(), start + CHUNK_LINES);
                String text = String.join("\n", lines.subList(start, end)).strip();
                if (!text.isEmpty()) chunks.add(new Chunk(relative, start + 1, end, text, tokens(relative + " " + text)));
                if (end == lines.size()) break;
            }
        } catch (IOException ignored) { }
    }

    private double score(Chunk chunk, List<String> query, Map<String, Integer> df, int documents, double averageLength) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String term : chunk.terms()) frequency.merge(term, 1, Integer::sum);
        double score = 0.0;
        double k1 = 1.2, b = 0.75;
        for (String term : query) {
            int tf = frequency.getOrDefault(term, 0);
            if (tf == 0) continue;
            double idf = Math.log(1.0 + (documents - df.getOrDefault(term, 0) + 0.5) / (df.getOrDefault(term, 0) + 0.5));
            score += idf * tf * (k1 + 1.0) / (tf + k1 * (1.0 - b + b * chunk.terms().size() / averageLength));
        }
        return score;
    }

    static List<String> tokens(String text) {
        var result = new ArrayList<String>();
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.codePoints().allMatch(LocalRagService::isHan)) {
                int[] points = token.codePoints().toArray();
                for (int point : points) result.add(new String(Character.toChars(point)));
                for (int i = 0; i + 1 < points.length; i++) result.add(new String(points, i, 2));
            } else if (token.length() >= 2) result.add(token);
        }
        return result;
    }

    private static boolean isHan(int codePoint) {
        return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    private record Chunk(String path, int startLine, int endLine, String text, List<String> terms) { }
    private record ScoredChunk(Chunk chunk, double score) { }
}

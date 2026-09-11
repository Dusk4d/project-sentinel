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
    private static final Set<String> QUERY_STOP_TERMS = Set.of("这", "个", "的", "了", "吗", "呢", "是", "什", "么", "哪些", "什么", "这个", "一下", "请问", "tell", "me", "the", "a", "an", "of", "is", "what");
    private final WorkspaceGuard guard;
    private final Map<Path, CachedFile> fileCache = new HashMap<>();
    private long indexedFiles;

    public LocalRagService(WorkspaceGuard guard) { this.guard = guard; }

    public RagAnswer ask(String question) throws IOException {
        String query = question == null ? "" : question.strip();
        if (query.isEmpty()) throw new IllegalArgumentException("RAG 问题不能为空");
        IndexSnapshot snapshot = index();
        List<Chunk> chunks = snapshot.chunks();
        QueryIntent intent = QueryIntent.classify(query);
        List<String> queryTerms = queryTerms(query, intent);
        if (queryTerms.isEmpty() || chunks.isEmpty()) return noEvidence(query, snapshot);

        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Chunk chunk : chunks) {
            for (String term : new HashSet<>(chunk.terms())) documentFrequency.merge(term, 1, Integer::sum);
        }
        double averageLength = chunks.stream().mapToInt(chunk -> chunk.terms().size()).average().orElse(1.0);
        List<ScoredChunk> ranked = chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, queryTerms, documentFrequency,
                        chunks.size(), averageLength, intent)))
                .filter(scored -> scored.score() > 0.0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(scored -> scored.chunk().path()).thenComparingInt(scored -> scored.chunk().startLine()))
                .toList();
        List<RagHit> hits = diverseHits(ranked);
        if (hits.isEmpty()) return noEvidence(query, snapshot);
        String extract = cleanExcerpt(hits.get(0).text());
        if (extract.length() > 360) extract = extract.substring(0, 357) + "...";
        return answer(query, "最相关的本地证据是：" + extract + "（请结合下方来源核验。）", hits, snapshot);
    }

    private RagAnswer noEvidence(String query, IndexSnapshot snapshot) {
        return answer(query, "没有找到足以支持回答的本地证据。", List.of(), snapshot);
    }

    private RagAnswer answer(String query, String text, List<RagHit> hits, IndexSnapshot snapshot) {
        if (snapshot.truncated()) text += " 注意：RAG 索引已达到安全上限，结果可能不完整。";
        return new RagAnswer(1, query, text, hits, snapshot.files(), snapshot.chunks().size(), snapshot.truncated());
    }

    private String cleanExcerpt(String text) {
        return text.replaceAll("<[^>]+>", " ")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("[`*_]", "")
                .replaceAll("\\s+", " ").strip();
    }

    private synchronized IndexSnapshot index() throws IOException {
        var chunks = new ArrayList<Chunk>();
        var seen = new HashSet<Path>();
        int[] files = {0};
        boolean[] truncated = {false};
        Files.walkFileTree(guard.root(), new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(guard.root()) && IGNORED_DIRECTORIES.contains(dir.getFileName().toString().toLowerCase(Locale.ROOT)))
                    return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!safeTextFile(file)) return FileVisitResult.CONTINUE;
                if (files[0] >= MAX_FILES) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                files[0]++;
                try {
                    Path real = file.toRealPath();
                    seen.add(real);
                    FileStamp stamp = new FileStamp(attrs.size(), attrs.lastModifiedTime());
                    CachedFile cached = fileCache.get(real);
                    if (cached == null || !cached.stamp().equals(stamp)) {
                        cached = new CachedFile(stamp, chunksFor(real));
                        fileCache.put(real, cached);
                        indexedFiles++;
                    }
                    int remaining = MAX_CHUNKS - chunks.size();
                    if (cached.chunks().size() > remaining) truncated[0] = true;
                    chunks.addAll(cached.chunks().subList(0, Math.min(remaining, cached.chunks().size())));
                } catch (IOException ignored) { }
                return truncated[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException exc) { return FileVisitResult.CONTINUE; }
        });
        fileCache.keySet().retainAll(seen);
        return new IndexSnapshot(List.copyOf(chunks), files[0], truncated[0]);
    }

    long indexedFiles() { return indexedFiles; }

    private boolean safeTextFile(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES || !file.toRealPath().startsWith(guard.root())) return false;
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.equals(".env") || name.startsWith(".env.") || name.contains("credential") || name.endsWith(".pem") || name.endsWith(".p12") || name.endsWith(".pfx")) return false;
            return TEXT_NAMES.contains(name) || TEXT_NAMES.stream().anyMatch(base -> name.startsWith(base + "."))
                    || TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
        } catch (IOException ignored) { return false; }
    }

    private List<Chunk> chunksFor(Path file) {
        var chunks = new ArrayList<Chunk>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = guard.root().relativize(file.toRealPath()).toString().replace('\\', '/');
            for (int start = 0; start < lines.size() && chunks.size() <= MAX_CHUNKS; start += CHUNK_STEP) {
                int end = Math.min(lines.size(), start + CHUNK_LINES);
                String text = String.join("\n", lines.subList(start, end)).strip();
                if (!text.isEmpty()) chunks.add(new Chunk(relative, start + 1, end, text, tokens(relative + " " + text)));
                if (end == lines.size()) break;
            }
        } catch (IOException ignored) { }
        return List.copyOf(chunks);
    }

    private double score(Chunk chunk, List<String> query, Map<String, Integer> df, int documents,
                         double averageLength, QueryIntent intent) {
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
        return intent.adjust(chunk, score);
    }

    private List<String> queryTerms(String query, QueryIntent intent) {
        var terms = new ArrayList<>(tokens(query).stream().filter(term -> !QUERY_STOP_TERMS.contains(term)).toList());
        terms.addAll(tokens(intent.expansion));
        return terms.stream().distinct().toList();
    }

    private List<RagHit> diverseHits(List<ScoredChunk> ranked) {
        var hits = new ArrayList<RagHit>();
        var perPath = new HashMap<String, Integer>();
        for (ScoredChunk scored : ranked) {
            Chunk chunk = scored.chunk();
            if (perPath.getOrDefault(chunk.path(), 0) >= 2) continue;
            boolean overlaps = hits.stream().anyMatch(hit -> hit.path().equals(chunk.path())
                    && hit.startLine() <= chunk.endLine() && chunk.startLine() <= hit.endLine());
            if (overlaps) continue;
            hits.add(new RagHit(chunk.path(), chunk.startLine(), chunk.endLine(), scored.score(), chunk.text()));
            perPath.merge(chunk.path(), 1, Integer::sum);
            if (hits.size() == 5) break;
        }
        return List.copyOf(hits);
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

    private enum QueryIntent {
        OVERVIEW("readme 简介 定位 目标 核心 功能 overview purpose"),
        STARTUP("readme 启动 运行 安装 使用 start run install"),
        ARCHITECTURE("architecture 架构 模块 组件 设计 分层"),
        TECHNOLOGY("pom package pyproject build 技术 技术栈 依赖 framework"),
        TESTING("test tests 测试 验证 coverage"),
        GENERAL("");

        private final String expansion;

        QueryIntent(String expansion) { this.expansion = expansion; }

        static QueryIntent classify(String question) {
            String text = question.toLowerCase(Locale.ROOT);
            if (containsAny(text, "主要功能", "核心功能", "项目是什么", "做什么", "项目介绍", "项目简介", "what is this project", "what does this project")) return OVERVIEW;
            if (containsAny(text, "怎么启动", "如何启动", "怎么运行", "如何运行", "安装", "部署", "start", "run", "launch")) return STARTUP;
            if (containsAny(text, "架构", "模块", "组件", "分层", "architecture", "module", "component")) return ARCHITECTURE;
            if (containsAny(text, "技术栈", "框架", "依赖", "technology", "framework", "dependency")) return TECHNOLOGY;
            if (containsAny(text, "测试", "覆盖率", "test", "coverage")) return TESTING;
            return GENERAL;
        }

        double adjust(Chunk chunk, double lexical) {
            String path = chunk.path().toLowerCase(Locale.ROOT);
            String text = chunk.text().toLowerCase(Locale.ROOT);
            double adjusted = lexical;
            if (this == OVERVIEW) {
                if (rootReadme(path)) adjusted += chunk.startLine() == 1 ? 30.0 : chunk.startLine() <= 80 ? 14.0 : 6.0;
                else if (path.endsWith("vision.md")) adjusted += 9.0;
                else if (path.endsWith("architecture.md")) adjusted += 5.0;
                else if (isBuildManifest(path)) adjusted += 2.0;
                else adjusted *= 0.25;
                if (text.contains("## 目录") || occurrences(text, "](#") >= 3) adjusted *= 0.35;
                if (path.startsWith("interview-prep/") || path.startsWith("tmp/") || path.startsWith("reports/")) adjusted *= 0.2;
            } else if (this == STARTUP) {
                if (rootReadme(path)) adjusted += 7.0;
                if (containsAny(path, "start", "run", "docker", "makefile") || containsAny(text, "启动", "运行", "start", "docker compose")) adjusted += 5.0;
            } else if (this == ARCHITECTURE) {
                if (path.endsWith("architecture.md")) adjusted += 10.0;
                else if (rootReadme(path)) adjusted += 4.0;
            } else if (this == TECHNOLOGY) {
                if (isBuildManifest(path)) adjusted += 10.0;
                else if (rootReadme(path)) adjusted += 4.0;
            } else if (this == TESTING) {
                if (path.contains("/test/") || path.startsWith("test/") || path.endsWith("test.java") || path.endsWith("_test.py")) adjusted += 5.0;
            }
            return adjusted;
        }

        private static boolean containsAny(String text, String... candidates) {
            for (String candidate : candidates) if (text.contains(candidate)) return true;
            return false;
        }

        private static int occurrences(String text, String needle) {
            int count = 0;
            for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
            return count;
        }

        private static boolean rootReadme(String path) {
            return !path.contains("/") && (path.equals("readme") || path.startsWith("readme."));
        }

        private static boolean isBuildManifest(String path) {
            return !path.contains("/") && (path.equals("pom.xml") || path.equals("package.json")
                    || path.equals("pyproject.toml") || path.equals("build.gradle") || path.equals("go.mod")
                    || path.equals("cargo.toml"));
        }
    }

    private record Chunk(String path, int startLine, int endLine, String text, List<String> terms) { }
    private record ScoredChunk(Chunk chunk, double score) { }
    private record FileStamp(long size, java.nio.file.attribute.FileTime modifiedTime) { }
    private record CachedFile(FileStamp stamp, List<Chunk> chunks) { }
    private record IndexSnapshot(List<Chunk> chunks, int files, boolean truncated) { }
}

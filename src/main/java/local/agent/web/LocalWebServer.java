package local.agent.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.analysis.ProjectDiscovery;
import local.agent.analysis.ProjectDiscoveryResult;
import local.agent.report.JsonReportWriter;
import local.agent.report.AnalysisBundleJsonWriter;
import local.agent.WorkspaceGuard;
import local.agent.rag.LocalRagService;
import local.agent.model.AiRagService;
import local.agent.model.ModelConfig;
import local.agent.model.OpenAiCompatibleClient;
import local.agent.model.ToolCallingAgentService;
import local.agent.model.AgentMemoryEntry;
import local.agent.model.AgentMemoryStore;
import local.agent.model.AgentCheckpointStore;
import local.agent.WorkspaceAgent;
import local.agent.state.StateRunLock;
import local.agent.state.RunAlreadyActiveException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalWebServer implements AutoCloseable {
    private static final int MAX_WEB_PROJECTS = 200;
    private static final int MAX_RAG_QUESTION_BYTES = 8 * 1024;
    private final Path workspace;
    private final List<Path> projects;
    private final boolean projectsTruncated;
    private final ScanAdmissionGate scanGate;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ModelConfig modelConfig;
    private final Path agentStateDirectory;

    public LocalWebServer(Path workspace, int port) throws IOException {
        this(workspace, port, new ScanAdmissionGate(), ModelConfig.optionalFromEnvironment().orElse(null), null);
    }

    public LocalWebServer(Path workspace, int port, ModelConfig modelConfig) throws IOException {
        this(workspace, port, new ScanAdmissionGate(), java.util.Objects.requireNonNull(modelConfig, "modelConfig"), null);
    }

    public LocalWebServer(Path workspace, int port, Path agentStateDirectory) throws IOException {
        this(workspace, port, new ScanAdmissionGate(), ModelConfig.optionalFromEnvironment().orElse(null),
                java.util.Objects.requireNonNull(agentStateDirectory, "agentStateDirectory"));
    }

    public LocalWebServer(Path workspace, int port, ModelConfig modelConfig, Path agentStateDirectory) throws IOException {
        this(workspace, port, new ScanAdmissionGate(), java.util.Objects.requireNonNull(modelConfig, "modelConfig"),
                java.util.Objects.requireNonNull(agentStateDirectory, "agentStateDirectory"));
    }

    LocalWebServer(Path workspace, int port, ScanAdmissionGate scanGate) throws IOException {
        this(workspace, port, scanGate, null, null);
    }

    private LocalWebServer(Path workspace, int port, ScanAdmissionGate scanGate, ModelConfig modelConfig,
                           Path agentStateDirectory) throws IOException {
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("端口必须在 0 到 65535 之间");
        this.scanGate = java.util.Objects.requireNonNull(scanGate, "scanGate");
        this.modelConfig = modelConfig;
        this.workspace = workspace.toRealPath();
        if (!Files.isDirectory(this.workspace)) throw new IOException("工作区不是目录: " + this.workspace);
        this.agentStateDirectory = agentStateDirectory == null ? null : validatedExternalState(agentStateDirectory);
        ProjectDiscoveryResult discovery = new ProjectDiscovery().discoverBounded(this.workspace,
                ProjectDiscovery.DEFAULT_MAX_DEPTH, MAX_WEB_PROJECTS);
        this.projects = discovery.projects().isEmpty() ? List.of(this.workspace) : discovery.projects();
        this.projectsTruncated = discovery.truncated();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        createContext("/api/health", this::health);
        createContext("/api/projects", this::projects);
        createContext("/api/analysis", this::analysis);
        createContext("/api/upload-analysis", this::uploadAnalysis);
        createContext("/api/rag", this::rag);
        createContext("/api/rag-ai", this::ragAi);
        createContext("/api/agent-ai", this::agentAi);
        createContext("/api/report", this::report);
        createContext("/", this::page);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    public String url() { return "http://127.0.0.1:" + port() + "/"; }

    private Path validatedExternalState(Path candidate) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path existing = normalized;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) throw new IOException("无法解析 Web Agent 状态目录");
        Path resolved = existing.toRealPath().resolve(existing.relativize(normalized)).normalize();
        if (resolved.startsWith(workspace)) throw new IOException("Web Agent 状态目录必须位于启动工作区之外");
        return normalized;
    }

    private void createContext(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            if (!allowedHost(exchange.getRequestHeaders().getFirst("Host"))) {
                send(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"invalid host\"}\n");
                return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !allowedOrigin(origin, port())) {
                send(exchange, 403, "application/json; charset=utf-8", "{\"error\":\"cross-origin request denied\"}\n");
                return;
            }
            handler.handle(exchange);
        });
    }

    static boolean allowedHost(String value) {
        if (value == null || value.isBlank()) return false;
        String host = value.strip().toLowerCase(java.util.Locale.ROOT);
        if (host.startsWith("[::1]")) return host.length() == 5 || validPortSuffix(host.substring(5));
        int colon = host.lastIndexOf(':');
        String name = colon < 0 ? host : host.substring(0, colon);
        if (colon >= 0 && !validPortSuffix(host.substring(colon))) return false;
        return name.equals("127.0.0.1") || name.equals("localhost");
    }

    static boolean allowedOrigin(String value, int serverPort) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return false;
        try {
            URI origin = URI.create(value.strip());
            if (!"http".equalsIgnoreCase(origin.getScheme()) || origin.getUserInfo() != null
                    || origin.getQuery() != null || origin.getFragment() != null) return false;
            String path = origin.getPath();
            if (path != null && !path.isEmpty()) return false;
            String host = origin.getHost();
            if (host == null || !(host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                    || host.equals("::1") || host.equals("[::1]")))
                return false;
            return (origin.getPort() < 0 ? 80 : origin.getPort()) == serverPort;
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static boolean validPortSuffix(String suffix) {
        if (suffix.length() < 2 || suffix.charAt(0) != ':') return false;
        try {
            int port = Integer.parseInt(suffix.substring(1));
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException ignored) { return false; }
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/health")) { notFound(exchange); return; }
        if (!method(exchange, "GET")) return;
        send(exchange, 200, "application/json; charset=utf-8",
                "{\"status\":\"UP\",\"workspace\":" + JsonReportWriter.quote(workspace.toString())
                        + ",\"projectCount\":" + projects.size() + ",\"projectsTruncated\":" + projectsTruncated
                        + ",\"scanBusy\":" + scanGate.busy() + ",\"modelEnabled\":" + (modelConfig != null)
                        + ",\"agentMemoryEnabled\":" + (agentStateDirectory != null) + "}\n");
    }

    private void projects(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/projects")) { notFound(exchange); return; }
        if (!method(exchange, "GET")) return;
        var out = new StringBuilder("{\"projects\":[");
        for (int index = 0; index < projects.size(); index++) {
            if (index > 0) out.append(',');
            Path project = projects.get(index);
            out.append("{\"id\":").append(JsonReportWriter.quote(projectId(project)))
                    .append(",\"name\":").append(JsonReportWriter.quote(project.getFileName().toString()))
                    .append(",\"path\":").append(JsonReportWriter.quote(project.toString())).append('}');
        }
        send(exchange, 200, "application/json; charset=utf-8",
                out.append("],\"truncated\":").append(projectsTruncated)
                        .append(",\"maximumProjects\":").append(MAX_WEB_PROJECTS)
                        .append(",\"modelEnabled\":").append(modelConfig != null)
                        .append(",\"agentMemoryEnabled\":").append(agentStateDirectory != null).append("}\n").toString());
    }

    private void report(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/report")) { notFound(exchange); return; }
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("POST")) { methodNotAllowed(exchange, "GET, POST"); return; }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease) {
            Path project = selectedProject(exchange);
            if (project == null) {
                send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"unknown project id\"}\n");
                return;
            }
            String json = new JsonReportWriter().render(new ProjectAnalyzer().analyze(project));
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (Exception e) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private void analysis(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/analysis")) { notFound(exchange); return; }
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("POST")) { methodNotAllowed(exchange, "GET, POST"); return; }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease) {
            Path project = selectedProject(exchange);
            if (project == null) {
                send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"unknown project id\"}\n");
                return;
            }
            String json = new AnalysisBundleJsonWriter().render(new ProjectAnalyzer().analyze(project));
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (Exception e) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private void page(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/")) { notFound(exchange); return; }
        if (!method(exchange, "GET")) return;
        send(exchange, 200, "text/html; charset=utf-8", HTML);
    }

    private void rag(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/rag")) { notFound(exchange); return; }
        if (!method(exchange, "POST")) return;
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/plain")) {
            send(exchange, 415, "application/json; charset=utf-8", "{\"error\":\"Content-Type must be text/plain\"}\n");
            return;
        }
        Path project = selectedProject(exchange);
        if (project == null) {
            send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"unknown project id\"}\n");
            return;
        }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease) {
            String question = readTextBody(exchange, MAX_RAG_QUESTION_BYTES);
            String json = new LocalRagService(new WorkspaceGuard(project)).ask(question).toJson();
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (RequestTooLargeException tooLarge) {
            send(exchange, 413, "application/json; charset=utf-8", "{\"error\":\"question exceeds 8 KiB limit\"}\n");
        } catch (IllegalArgumentException invalid) {
            send(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(invalid.getMessage()) + "}\n");
        } catch (Exception e) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private void ragAi(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/rag-ai")) { notFound(exchange); return; }
        if (!method(exchange, "POST")) return;
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/plain")) {
            send(exchange, 415, "application/json; charset=utf-8", "{\"error\":\"Content-Type must be text/plain\"}\n");
            return;
        }
        if (modelConfig == null) {
            send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"model enhancement is not configured\"}\n");
            return;
        }
        Path project = selectedProject(exchange);
        if (project == null) {
            send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"unknown project id\"}\n");
            return;
        }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease) {
            String question = readTextBody(exchange, MAX_RAG_QUESTION_BYTES);
            var local = new LocalRagService(new WorkspaceGuard(project));
            String json = new AiRagService(local, new OpenAiCompatibleClient(modelConfig)).ask(question).toJson();
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (RequestTooLargeException tooLarge) {
            send(exchange, 413, "application/json; charset=utf-8", "{\"error\":\"question exceeds 8 KiB limit\"}\n");
        } catch (IllegalArgumentException invalid) {
            send(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(invalid.getMessage()) + "}\n");
        } catch (Exception e) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private void agentAi(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/agent-ai")) { notFound(exchange); return; }
        if (!method(exchange, "POST")) return;
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/plain")) {
            send(exchange, 415, "application/json; charset=utf-8", "{\"error\":\"Content-Type must be text/plain\"}\n");
            return;
        }
        if (modelConfig == null) {
            send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"function calling model is not configured\"}\n");
            return;
        }
        Path project = selectedProject(exchange);
        if (project == null) {
            send(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"unknown project id\"}\n");
            return;
        }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease) {
            String task = readTextBody(exchange, MAX_RAG_QUESTION_BYTES);
            var agent = new ToolCallingAgentService(new WorkspaceAgent(project), new OpenAiCompatibleClient(modelConfig));
            if (agentStateDirectory == null) {
                send(exchange, 200, "application/json; charset=utf-8", agent.run(task).toJson());
            } else {
                Path state = agentStateDirectory.resolve(projectId(project));
                try (var ignored = StateRunLock.acquire(state, "web-agent-ai")) {
                    var memory = new AgentMemoryStore(project, state);
                    var checkpoints = new AgentCheckpointStore(project, state);
                    boolean recoveringReady = checkpoints.loadPendingResult(task).isPresent();
                    var result = agent.runResumable(task, memory.readRecent(5), checkpoints);
                    var entry = new AgentMemoryEntry(java.time.Instant.now(), task, result.answer(),
                            result.modelRounds(), result.toolCalls());
                    if (recoveringReady) memory.appendIfLastEquivalent(entry); else memory.append(entry);
                    checkpoints.markCompleted(task);
                    send(exchange, 200, "application/json; charset=utf-8", result.toJson());
                }
            }
        } catch (RequestTooLargeException tooLarge) {
            send(exchange, 413, "application/json; charset=utf-8", "{\"error\":\"task exceeds 8 KiB limit\"}\n");
        } catch (IllegalArgumentException invalid) {
            send(exchange, 400, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(invalid.getMessage()) + "}\n");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            send(exchange, 503, "application/json; charset=utf-8", "{\"error\":\"agent request interrupted\"}\n");
        } catch (RunAlreadyActiveException busy) {
            exchange.getResponseHeaders().set("Retry-After", "1");
            send(exchange, 429, "application/json; charset=utf-8", "{\"error\":\"agent state already in use\"}\n");
        } catch (Exception e) {
            send(exchange, 502, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private String readTextBody(HttpExchange exchange, int maximumBytes) throws IOException {
        long declared = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declared > maximumBytes) throw new RequestTooLargeException();
        byte[] bytes = exchange.getRequestBody().readNBytes(maximumBytes + 1);
        if (bytes.length > maximumBytes) throw new RequestTooLargeException();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void uploadAnalysis(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/upload-analysis")) { notFound(exchange); return; }
        if (!method(exchange, "POST")) return;
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.equalsIgnoreCase("application/zip")) {
            send(exchange, 415, "application/json; charset=utf-8", "{\"error\":\"Content-Type must be application/zip\"}\n");
            return;
        }
        var lease = scanGate.tryAcquire();
        if (lease == null) { busy(exchange); return; }
        try (lease; var upload = ZipProjectUpload.extract(exchange.getRequestBody(), workspace,
                parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length")))) {
            String json = new AnalysisBundleJsonWriter().render(new ProjectAnalyzer().analyze(upload.projectRoot()));
            send(exchange, 200, "application/json; charset=utf-8", json);
        } catch (ZipProjectUpload.UploadRejectedException rejected) {
            send(exchange, 413, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(rejected.getMessage()) + "}\n");
        } catch (Exception e) {
            send(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":" + JsonReportWriter.quote(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "}\n");
        }
    }

    private long parseContentLength(String value) {
        if (value == null) return -1;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static final class RequestTooLargeException extends IOException { }

    private boolean exactPath(HttpExchange exchange, String expected) {
        return exchange.getRequestURI().getPath().equals(expected);
    }

    private Path selectedProject(HttpExchange exchange) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) return projects.get(0);
        if (!query.startsWith("project=") || query.indexOf('&') >= 0) return null;
        String requested;
        try { requested = java.net.URLDecoder.decode(query.substring("project=".length()), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException malformedEncoding) { return null; }
        return projects.stream().filter(path -> projectId(path).equals(requested)).findFirst().orElse(null);
    }

    private String projectId(Path project) {
        String relative = workspace.relativize(project).toString().replace('\\', '/');
        if (relative.isEmpty()) return "root";
        return "p-" + Base64.getUrlEncoder().withoutPadding().encodeToString(relative.getBytes(StandardCharsets.UTF_8));
    }

    private boolean method(HttpExchange exchange, String expected) throws IOException {
        if (exchange.getRequestMethod().equals(expected)) return true;
        methodNotAllowed(exchange, expected);
        return false;
    }

    private void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"method not allowed\"}\n");
    }

    private void notFound(HttpExchange exchange) throws IOException {
        send(exchange, 404, "application/json; charset=utf-8", "{\"error\":\"not found\"}\n");
    }

    private void busy(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Retry-After", "1");
        send(exchange, 429, "application/json; charset=utf-8", "{\"error\":\"scan already in progress\"}\n");
    }

    private void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    @Override public void close() {
        server.stop(0);
        executor.close();
    }

    private static final String HTML = """
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Project Sentinel</title><style>
            :root{color-scheme:light;font-family:Inter,"Microsoft YaHei",sans-serif;background:#f4f7fb;color:#172033}body{margin:0}.wrap{max-width:1050px;margin:auto;padding:32px 20px}header{display:flex;justify-content:space-between;align-items:center;gap:16px}h1{margin:0;font-size:28px}.controls{display:flex;flex-wrap:wrap;gap:10px}button,select,input{border:1px solid #cbd5e1;border-radius:10px;padding:11px 14px;font-weight:700;background:white}button{border:0;background:#2457d6;color:white;cursor:pointer}button:disabled{opacity:.55}.grid{display:grid;grid-template-columns:220px 1fr;gap:18px;margin-top:24px}.card{background:white;border:1px solid #dce4f0;border-radius:16px;padding:20px;box-shadow:0 8px 24px #1b31500d}.score{font-size:64px;font-weight:800;color:#176b45}.muted{color:#667085}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric{background:#f6f8fc;border-radius:10px;padding:12px}.finding,.evidence{border-top:1px solid #e7ebf2;padding:14px 0}.finding:first-child,.evidence:first-child{border:0}.sev{font-size:12px;font-weight:800;padding:3px 8px;border-radius:99px;background:#eef2ff}.error{color:#b42318}.ask{display:flex;gap:10px}.ask input{min-width:0;flex:1}.answer{white-space:pre-wrap;line-height:1.65}@media(max-width:700px){.grid{grid-template-columns:1fr}.metrics{grid-template-columns:1fr 1fr}header{align-items:flex-start;flex-direction:column}.controls{width:100%}select{min-width:0;flex:1}.ask{flex-direction:column}}
            </style></head><body><main class="wrap"><header><div><h1>Project Sentinel</h1><div id="project" class="muted">正在连接本地后端…</div><div id="catalog" class="muted"></div></div><div class="controls"><select id="projects" aria-label="选择项目"></select><button id="scan">重新扫描</button><input id="zip" type="file" accept=".zip,application/zip" hidden><button id="upload">上传 ZIP 检测</button><button id="download" disabled>下载 JSON</button></div></header><section class="grid"><div class="card"><div class="muted">健康分</div><div id="score" class="score">--</div><div id="ecosystem" class="muted"></div><div id="projection" class="muted"></div></div><div class="card"><div class="metrics"><div class="metric">文件<br><strong id="files">--</strong></div><div class="metric">源码<br><strong id="sources">--</strong></div><div class="metric">测试<br><strong id="tests">--</strong></div><div class="metric">待办<br><strong id="todos">--</strong></div></div><h2>项目问答（RAG）</h2><div class="ask"><input id="question" maxlength="8000" placeholder="例如：这个项目如何启动？" aria-label="项目问题"><button id="ask">问项目</button></div><label class="muted"><input id="ai" type="checkbox" disabled> 使用模型增强</label><span id="model-state" class="muted">（未配置模型）</span><div id="rag-answer" class="answer muted">答案会基于当前项目文件生成，并附带证据位置。</div><div id="rag-evidence"></div><h2>优先行动</h2><div id="actions"></div><h2>全部发现</h2><div id="findings"></div></div></section></main><script>
            const $=id=>document.getElementById(id);
            let latest=null;
            function render(bundle){
              latest=bundle;$('download').disabled=false;
              const d=bundle.report,p=bundle.plan;
              $('project').textContent=d.root;$('score').textContent=d.healthScore;$('ecosystem').textContent=d.ecosystem;
              $('projection').textContent=p.actionCount?'完成行动后理论预计 '+p.projectedHealthScore+' 分（可恢复 '+p.potentialScoreRecovery+'）':'当前没有需处理的行动';
              for(const k of ['files','sources','tests','todos'])$(k).textContent=d.metrics[k];
              $('actions').replaceChildren(...p.actions.map(a=>{const x=document.createElement('div');x.className='finding';const h=document.createElement('strong');h.textContent=a.rank+'. '+a.action;const e=document.createElement('div');e.className='muted';e.textContent=a.severity+' · '+a.category+' · 预计恢复 '+a.potentialScoreGain+' 分';const why=document.createElement('div');why.textContent=a.rationale;x.append(h,e,why);return x}));
              $('findings').replaceChildren(...d.findings.map(f=>{const x=document.createElement('div');x.className='finding';const h=document.createElement('div');const s=document.createElement('span');s.className='sev';s.textContent=f.severity;h.append(s,' '+f.category+' · '+f.message);const e=document.createElement('div');e.className='muted';e.textContent='证据：'+f.evidence;const a=document.createElement('div');a.textContent='建议：'+f.action;x.append(h,e,a);return x}));
            }
            function showError(e){$('actions').replaceChildren();$('findings').innerHTML='<div class="error"></div>';$('findings').firstChild.textContent=e.message}
            async function scan(){const b=$('scan');b.disabled=true;b.textContent='扫描中…';try{const id=encodeURIComponent($('projects').value);const r=await fetch('/api/analysis?project='+id,{method:'POST'});const bundle=await r.json();if(!r.ok)throw Error(bundle.error||'扫描失败');render(bundle)}catch(e){showError(e)}finally{b.disabled=false;b.textContent='重新扫描'}}
            async function upload(file){if(file.size>20*1024*1024){showError(Error('ZIP 不能超过 20 MiB'));return}const b=$('upload');b.disabled=true;b.textContent='上传检测中…';try{const r=await fetch('/api/upload-analysis',{method:'POST',headers:{'Content-Type':'application/zip'},body:file});const bundle=await r.json();if(!r.ok)throw Error(bundle.error||'上传检测失败');render(bundle)}catch(e){showError(e)}finally{b.disabled=false;b.textContent='上传 ZIP 检测';$('zip').value=''}}
            async function ask(){const q=$('question').value.trim();if(!q){$('rag-answer').textContent='请先输入问题。';return}const b=$('ask');b.disabled=true;b.textContent='检索中…';$('rag-answer').classList.remove('error');$('rag-answer').textContent='正在读取项目并查找证据…';$('rag-evidence').replaceChildren();try{const id=encodeURIComponent($('projects').value);const endpoint=$('ai').checked?'/api/rag-ai':'/api/rag';const r=await fetch(endpoint+'?project='+id,{method:'POST',headers:{'Content-Type':'text/plain; charset=utf-8'},body:q});const data=await r.json();if(!r.ok)throw Error(data.error||'问答失败');$('rag-answer').textContent=data.answer+(data.notice?'\\n\\n'+data.notice:'');const hits=data.evidence||[];$('rag-evidence').replaceChildren(...hits.map(hit=>{const x=document.createElement('div');x.className='evidence';const h=document.createElement('strong');h.textContent=hit.path+':'+hit.startLine+'-'+hit.endLine;const score=document.createElement('div');score.className='muted';score.textContent='相关度 '+Number(hit.score).toFixed(3);const body=document.createElement('div');body.className='answer';body.textContent=hit.text;x.append(h,score,body);return x}))}catch(e){$('rag-answer').textContent=e.message;$('rag-answer').classList.add('error')}finally{b.disabled=false;b.textContent='问项目'}}
            function download(){if(!latest)return;const safe=(latest.report.project||'project').replace(/[^a-zA-Z0-9._-]+/g,'-');const blob=new Blob([JSON.stringify(latest,null,2)+'\\n'],{type:'application/json;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=safe+'-sentinel-analysis.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),0)}
            async function init(){try{const r=await fetch('/api/projects');const d=await r.json();$('catalog').textContent=d.truncated?'项目超过 '+d.maximumProjects+' 个，仅显示前 '+d.maximumProjects+' 个；请缩小启动工作区。':'';$('ai').disabled=!d.modelEnabled;$('model-state').textContent=d.modelEnabled?'（已配置，勾选后启用）':'（未配置模型，使用本地抽取式回答）';$('projects').replaceChildren(...d.projects.map(p=>{const o=document.createElement('option');o.value=p.id;o.textContent=p.name;return o}));$('projects').addEventListener('change',scan);await scan()}catch(e){$('project').textContent='后端连接失败';$('findings').textContent=e.message}}
            $('scan').addEventListener('click',scan);$('ask').addEventListener('click',ask);$('question').addEventListener('keydown',e=>{if(e.key==='Enter')ask()});$('upload').addEventListener('click',()=>$('zip').click());$('zip').addEventListener('change',()=>{if($('zip').files[0])upload($('zip').files[0])});$('download').addEventListener('click',download);init();
            </script></body></html>
            """
            .replace("<h2>项目问答（RAG）</h2>", "<h2>项目智能助手</h2>")
            .replace("<button id=\"ask\">问项目</button>", "<button id=\"ask\">RAG 问答</button><button id=\"agent\" disabled>Agent 分析</button>")
            .replace("答案会基于当前项目文件生成，并附带证据位置。", "RAG 返回带位置的检索证据；Agent 会自主调用只读工具并汇总结果。")
            .replace("function download(){", """
                    async function runAgent(){const q=$('question').value.trim();if(!q){$('rag-answer').textContent='请先输入任务。';return}const b=$('agent');b.disabled=true;b.textContent='Agent 执行中…';$('rag-answer').classList.remove('error');$('rag-answer').textContent='模型正在选择并调用只读项目工具…';$('rag-evidence').replaceChildren();try{const id=encodeURIComponent($('projects').value);const r=await fetch('/api/agent-ai?project='+id,{method:'POST',headers:{'Content-Type':'text/plain; charset=utf-8'},body:q});const data=await r.json();if(!r.ok)throw Error(data.error||'Agent 执行失败');$('rag-answer').textContent=data.answer+'\\n\\n模型轮次：'+data.modelRounds+'，工具调用：'+data.toolCalls}catch(e){$('rag-answer').textContent=e.message;$('rag-answer').classList.add('error')}finally{b.disabled=false;b.textContent='Agent 分析'}}
                    function download(){""")
            .replace("$('ai').disabled=!d.modelEnabled;", "$('ai').disabled=!d.modelEnabled;$('agent').disabled=!d.modelEnabled;")
            .replace("（已配置，勾选后启用）", "（模型已配置，可使用增强 RAG 与 Agent）")
            .replace("（未配置模型，使用本地抽取式回答）", "（未配置模型，使用本地 RAG）")
            .replace("d.modelEnabled?'（模型已配置，可使用增强 RAG 与 Agent）'", "d.modelEnabled?'（模型已配置，可使用增强 RAG 与 Agent'+(d.agentMemoryEnabled?'，已启用持久记忆':'')+'）'")
            .replace("+'，工具调用：'+data.toolCalls}catch", "+'，工具调用：'+data.toolCalls;const trace=data.trace||[];$('rag-evidence').replaceChildren(...trace.map(item=>{const x=document.createElement('div');x.className='evidence';x.textContent=item.sequence+'. '+item.name+(item.success?' · 成功':' · 失败');return x}))}catch")
            .replace("$('ask').addEventListener('click',ask);", "$('ask').addEventListener('click',ask);$('agent').addEventListener('click',runAgent);");
}

package local.agent.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.analysis.ProjectDiscovery;
import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalWebServer implements AutoCloseable {
    private final Path workspace;
    private final List<Path> projects;
    private final HttpServer server;
    private final ExecutorService executor;

    public LocalWebServer(Path workspace, int port) throws IOException {
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("端口必须在 0 到 65535 之间");
        this.workspace = workspace.toRealPath();
        if (!Files.isDirectory(this.workspace)) throw new IOException("工作区不是目录: " + this.workspace);
        List<Path> discovered = new ProjectDiscovery().discover(this.workspace);
        this.projects = discovered.isEmpty() ? List.of(this.workspace) : discovered;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/health", this::health);
        server.createContext("/api/projects", this::projects);
        server.createContext("/api/report", this::report);
        server.createContext("/", this::page);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    public String url() { return "http://127.0.0.1:" + port() + "/"; }

    private void health(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/health")) { notFound(exchange); return; }
        if (!method(exchange, "GET")) return;
        send(exchange, 200, "application/json; charset=utf-8",
                "{\"status\":\"UP\",\"workspace\":" + JsonReportWriter.quote(workspace.toString())
                        + ",\"projectCount\":" + projects.size() + "}\n");
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
        send(exchange, 200, "application/json; charset=utf-8", out.append("]}\n").toString());
    }

    private void report(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/report")) { notFound(exchange); return; }
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("POST")) { methodNotAllowed(exchange, "GET, POST"); return; }
        try {
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

    private void page(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/")) { notFound(exchange); return; }
        if (!method(exchange, "GET")) return;
        send(exchange, 200, "text/html; charset=utf-8", HTML);
    }

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
            <title>Workspace Agent</title><style>
            :root{color-scheme:light;font-family:Inter,"Microsoft YaHei",sans-serif;background:#f4f7fb;color:#172033}body{margin:0}.wrap{max-width:1050px;margin:auto;padding:32px 20px}header{display:flex;justify-content:space-between;align-items:center;gap:16px}h1{margin:0;font-size:28px}.controls{display:flex;gap:10px}button,select{border:1px solid #cbd5e1;border-radius:10px;padding:11px 14px;font-weight:700;background:white}button{border:0;background:#2457d6;color:white;cursor:pointer}button:disabled{opacity:.55}.grid{display:grid;grid-template-columns:220px 1fr;gap:18px;margin-top:24px}.card{background:white;border:1px solid #dce4f0;border-radius:16px;padding:20px;box-shadow:0 8px 24px #1b31500d}.score{font-size:64px;font-weight:800;color:#176b45}.muted{color:#667085}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric{background:#f6f8fc;border-radius:10px;padding:12px}.finding{border-top:1px solid #e7ebf2;padding:14px 0}.finding:first-child{border:0}.sev{font-size:12px;font-weight:800;padding:3px 8px;border-radius:99px;background:#eef2ff}.error{color:#b42318}@media(max-width:700px){.grid{grid-template-columns:1fr}.metrics{grid-template-columns:1fr 1fr}header{align-items:flex-start;flex-direction:column}.controls{width:100%}select{min-width:0;flex:1}}
            </style></head><body><main class="wrap"><header><div><h1>Project Sentinel</h1><div id="project" class="muted">正在连接本地后端…</div></div><div class="controls"><select id="projects" aria-label="选择项目"></select><button id="scan">重新扫描</button></div></header><section class="grid"><div class="card"><div class="muted">健康分</div><div id="score" class="score">--</div><div id="ecosystem" class="muted"></div></div><div class="card"><div class="metrics"><div class="metric">文件<br><strong id="files">--</strong></div><div class="metric">源码<br><strong id="sources">--</strong></div><div class="metric">测试<br><strong id="tests">--</strong></div><div class="metric">待办<br><strong id="todos">--</strong></div></div><h2>发现与建议</h2><div id="findings"></div></div></section></main><script>
            const $=id=>document.getElementById(id);async function scan(){const b=$('scan');b.disabled=true;b.textContent='扫描中…';try{const id=encodeURIComponent($('projects').value);const r=await fetch('/api/report?project='+id,{method:'POST'});const d=await r.json();if(!r.ok)throw Error(d.error||'扫描失败');$('project').textContent=d.root;$('score').textContent=d.healthScore;$('ecosystem').textContent=d.ecosystem;for(const k of ['files','sources','tests','todos'])$(k).textContent=d.metrics[k];$('findings').replaceChildren(...d.findings.map(f=>{const x=document.createElement('div');x.className='finding';const h=document.createElement('div');const s=document.createElement('span');s.className='sev';s.textContent=f.severity;h.append(s,' '+f.category+' · '+f.message);const e=document.createElement('div');e.className='muted';e.textContent='证据：'+f.evidence;const a=document.createElement('div');a.textContent='建议：'+f.action;x.append(h,e,a);return x}))}catch(e){$('findings').innerHTML='<div class="error"></div>';$('findings').firstChild.textContent=e.message}finally{b.disabled=false;b.textContent='重新扫描'}}async function init(){try{const r=await fetch('/api/projects');const d=await r.json();$('projects').replaceChildren(...d.projects.map(p=>{const o=document.createElement('option');o.value=p.id;o.textContent=p.name;return o}));$('projects').addEventListener('change',scan);await scan()}catch(e){$('project').textContent='后端连接失败';$('findings').textContent=e.message}}$('scan').addEventListener('click',scan);init();
            </script></body></html>
            """;
}

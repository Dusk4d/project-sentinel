package local.agent.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import local.agent.analysis.ProjectAnalyzer;
import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalWebServer implements AutoCloseable {
    private final Path project;
    private final HttpServer server;
    private final ExecutorService executor;

    public LocalWebServer(Path project, int port) throws IOException {
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("端口必须在 0 到 65535 之间");
        this.project = project.toRealPath();
        if (!Files.isDirectory(this.project)) throw new IOException("项目不是目录: " + this.project);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/api/health", this::health);
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
                "{\"status\":\"UP\",\"project\":" + JsonReportWriter.quote(project.toString()) + "}\n");
    }

    private void report(HttpExchange exchange) throws IOException {
        if (!exactPath(exchange, "/api/report")) { notFound(exchange); return; }
        String method = exchange.getRequestMethod();
        if (!method.equals("GET") && !method.equals("POST")) { methodNotAllowed(exchange, "GET, POST"); return; }
        try {
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
            :root{color-scheme:light;font-family:Inter,"Microsoft YaHei",sans-serif;background:#f4f7fb;color:#172033}body{margin:0}.wrap{max-width:1050px;margin:auto;padding:32px 20px}header{display:flex;justify-content:space-between;align-items:center;gap:16px}h1{margin:0;font-size:28px}button{border:0;border-radius:10px;background:#2457d6;color:white;padding:11px 18px;font-weight:700;cursor:pointer}button:disabled{opacity:.55}.grid{display:grid;grid-template-columns:220px 1fr;gap:18px;margin-top:24px}.card{background:white;border:1px solid #dce4f0;border-radius:16px;padding:20px;box-shadow:0 8px 24px #1b31500d}.score{font-size:64px;font-weight:800;color:#176b45}.muted{color:#667085}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.metric{background:#f6f8fc;border-radius:10px;padding:12px}.finding{border-top:1px solid #e7ebf2;padding:14px 0}.finding:first-child{border:0}.sev{font-size:12px;font-weight:800;padding:3px 8px;border-radius:99px;background:#eef2ff}.error{color:#b42318}@media(max-width:700px){.grid{grid-template-columns:1fr}.metrics{grid-template-columns:1fr 1fr}header{align-items:flex-start}}
            </style></head><body><main class="wrap"><header><div><h1>Workspace Agent</h1><div id="project" class="muted">正在连接本地后端…</div></div><button id="scan">重新扫描</button></header><section class="grid"><div class="card"><div class="muted">健康分</div><div id="score" class="score">--</div><div id="ecosystem" class="muted"></div></div><div class="card"><div class="metrics"><div class="metric">文件<br><strong id="files">--</strong></div><div class="metric">源码<br><strong id="sources">--</strong></div><div class="metric">测试<br><strong id="tests">--</strong></div><div class="metric">待办<br><strong id="todos">--</strong></div></div><h2>发现与建议</h2><div id="findings"></div></div></section></main><script>
            const $=id=>document.getElementById(id);async function scan(){const b=$('scan');b.disabled=true;b.textContent='扫描中…';try{const r=await fetch('/api/report',{method:'POST'});const d=await r.json();if(!r.ok)throw Error(d.error||'扫描失败');$('project').textContent=d.root;$('score').textContent=d.healthScore;$('ecosystem').textContent=d.ecosystem;for(const k of ['files','sources','tests','todos'])$(k).textContent=d.metrics[k];$('findings').replaceChildren(...d.findings.map(f=>{const x=document.createElement('div');x.className='finding';const h=document.createElement('div');const s=document.createElement('span');s.className='sev';s.textContent=f.severity;h.append(s,' '+f.category+' · '+f.message);const e=document.createElement('div');e.className='muted';e.textContent='证据：'+f.evidence;const a=document.createElement('div');a.textContent='建议：'+f.action;x.append(h,e,a);return x}))}catch(e){$('findings').innerHTML='<div class="error"></div>';$('findings').firstChild.textContent=e.message}finally{b.disabled=false;b.textContent='重新扫描'}}$('scan').addEventListener('click',scan);scan();
            </script></body></html>
            """;
}

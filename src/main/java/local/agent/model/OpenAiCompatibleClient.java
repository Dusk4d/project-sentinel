package local.agent.model;

import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiCompatibleClient {
    static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final ModelConfig config;
    private final HttpClient client;

    public OpenAiCompatibleClient(ModelConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.timeout()).build());
    }

    OpenAiCompatibleClient(ModelConfig config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    public String complete(String system, String user) throws IOException, InterruptedException {
        String body = "{\"model\":" + JsonReportWriter.quote(config.model())
                + ",\"temperature\":0.1,\"messages\":[{\"role\":\"system\",\"content\":"
                + JsonReportWriter.quote(system) + "},{\"role\":\"user\",\"content\":"
                + JsonReportWriter.quote(user) + "}]}";
        requireBoundedRequest(body);
        var builder = HttpRequest.newBuilder(config.endpoint()).timeout(config.timeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json");
        if (!config.apiKey().isEmpty()) builder.header("Authorization", "Bearer " + config.apiKey());
        var response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (var stream = response.body()) { bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
        if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("模型响应超过 2 MiB 限制");
        String json = new String(bytes, StandardCharsets.UTF_8);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw modelError(response.statusCode(), json);
        String content = extractContent(json);
        if (content.isBlank()) throw new IOException("模型响应缺少 choices[0].message.content");
        return content.strip();
    }

    ModelTurn completeTurn(String system, List<String> messageJson, String toolsJson) throws IOException, InterruptedException {
        String body = "{\"model\":" + JsonReportWriter.quote(config.model()) + ",\"temperature\":0.1,\"messages\":["
                + "{\"role\":\"system\",\"content\":" + JsonReportWriter.quote(system) + "},"
                + String.join(",", normalizeOutgoingMessages(messageJson))
                + "],\"tools\":" + toolsJson + ",\"tool_choice\":\"auto\"}";
        String response = send(body);
        Map<String, Object> root = JsonCodec.object(JsonCodec.parse(response), "响应根值");
        Object choicesValue = root.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) throw new IOException("模型响应缺少 choices[0]");
        Map<String, Object> choice = JsonCodec.object(choices.get(0), "choices[0]");
        Map<String, Object> message = JsonCodec.object(choice.get("message"), "choices[0].message");
        String content = message.get("content") instanceof String value ? value.strip() : "";
        var calls = new ArrayList<ModelToolCall>();
        var normalizedCalls = new ArrayList<Map<String, Object>>();
        Object callsValue = message.get("tool_calls");
        if (callsValue instanceof List<?> list) {
            if (list.size() > 8) throw new IOException("单轮模型工具调用超过 8 个限制");
            for (Object item : list) {
                Map<String, Object> call = JsonCodec.object(item, "tool_calls[]");
                Map<String, Object> function = JsonCodec.object(call.get("function"), "tool_calls[].function");
                String id = requiredString(call, "id");
                String name = requiredString(function, "name");
                String arguments = requiredString(function, "arguments");
                if (arguments.length() > 16 * 1024) throw new IOException("工具参数超过 16 KiB 限制");
                calls.add(new ModelToolCall(id, name, arguments));
                var normalizedFunction = new LinkedHashMap<String, Object>();
                normalizedFunction.put("name", name);
                normalizedFunction.put("arguments", arguments);
                var normalizedCall = new LinkedHashMap<String, Object>();
                normalizedCall.put("id", id);
                normalizedCall.put("type", "function");
                normalizedCall.put("function", normalizedFunction);
                normalizedCalls.add(normalizedCall);
            }
        }
        var normalized = new LinkedHashMap<String, Object>();
        normalized.put("role", "assistant");
        if (message.containsKey("content")) normalized.put("content", message.get("content"));
        if (!normalizedCalls.isEmpty()) normalized.put("tool_calls", normalizedCalls);
        return new ModelTurn(content, List.copyOf(calls), JsonCodec.write(normalized));
    }

    private static List<String> normalizeOutgoingMessages(List<String> messages) throws IOException {
        var normalized = new ArrayList<String>(messages.size());
        for (String json : messages) {
            Map<String, Object> message = JsonCodec.object(JsonCodec.parse(json), "模型消息");
            if (!"assistant".equals(message.get("role")) || !(message.get("tool_calls") instanceof List<?> calls)) {
                normalized.add(json);
                continue;
            }
            var cleanCalls = new ArrayList<Map<String, Object>>(calls.size());
            for (Object value : calls) {
                var clean = new LinkedHashMap<>(JsonCodec.object(value, "assistant.tool_calls[]"));
                clean.remove("index");
                cleanCalls.add(clean);
            }
            var cleanMessage = new LinkedHashMap<>(message);
            cleanMessage.put("tool_calls", cleanCalls);
            normalized.add(JsonCodec.write(cleanMessage));
        }
        return normalized;
    }

    private static String requiredString(Map<String, Object> object, String name) throws IOException {
        Object value = object.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IOException("模型工具调用缺少 " + name);
        return text;
    }

    private String send(String body) throws IOException, InterruptedException {
        requireBoundedRequest(body);
        var builder = requestBuilder();
        var response = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (var stream = response.body()) { bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
        if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("模型响应超过 2 MiB 限制");
        String json = new String(bytes, StandardCharsets.UTF_8);
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw modelError(response.statusCode(), json);
        return json;
    }

    private static IOException modelError(int status, String response) {
        String detail = "";
        try {
            Map<String, Object> root = JsonCodec.object(JsonCodec.parse(response), "模型错误响应");
            Object error = root.get("error");
            if (error instanceof String text) detail = text;
            else if (error instanceof Map<?, ?> object && object.get("message") instanceof String text) detail = text;
        } catch (Exception ignored) { }
        detail = detail.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        if (detail.length() > 300) detail = detail.substring(0, 300) + "…";
        return new IOException("模型服务返回 HTTP " + status + (detail.isEmpty() ? "" : "：" + detail));
    }

    private static void requireBoundedRequest(String body) throws IOException {
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES)
            throw new IOException("模型请求超过 1 MiB 限制");
    }

    private HttpRequest.Builder requestBuilder() {
        var builder = HttpRequest.newBuilder(config.endpoint()).timeout(config.timeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json");
        if (!config.apiKey().isEmpty()) builder.header("Authorization", "Bearer " + config.apiKey());
        return builder;
    }

    static String extractContent(String json) throws IOException {
        int choices = json.indexOf("\"choices\"");
        int message = choices < 0 ? -1 : json.indexOf("\"message\"", choices);
        int content = message < 0 ? -1 : json.indexOf("\"content\"", message);
        int colon = content < 0 ? -1 : json.indexOf(':', content + 9);
        int quote = colon < 0 ? -1 : skipWhitespace(json, colon + 1);
        if (quote < 0 || quote >= json.length() || json.charAt(quote) != '"')
            throw new IOException("模型响应缺少 choices[0].message.content");
        return parseString(json, quote);
    }

    private static int skipWhitespace(String text, int at) {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        return at;
    }

    private static String parseString(String json, int quote) throws IOException {
        var out = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (++i >= json.length()) break;
            char escaped = json.charAt(i);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= json.length()) throw new IOException("模型响应包含无效 Unicode 转义");
                    try { out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16)); }
                    catch (NumberFormatException e) { throw new IOException("模型响应包含无效 Unicode 转义", e); }
                    i += 4;
                }
                default -> throw new IOException("模型响应包含无效 JSON 转义");
            }
        }
        throw new IOException("模型响应包含未结束的 JSON 字符串");
    }
}

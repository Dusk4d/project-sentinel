package local.agent.model;

import local.agent.report.JsonReportWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonCodec {
    private JsonCodec() { }

    static Object parse(String json) throws IOException {
        var parser = new Parser(json);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.end()) throw new IOException("JSON 根值后存在多余内容");
        return value;
    }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return JsonReportWriter.quote(text);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof List<?> list)
            return "[" + String.join(",", list.stream().map(JsonCodec::write).toList()) + "]";
        if (value instanceof Map<?, ?> map) {
            var entries = new ArrayList<String>();
            for (var entry : map.entrySet()) entries.add(JsonReportWriter.quote(String.valueOf(entry.getKey())) + ":" + write(entry.getValue()));
            return "{" + String.join(",", entries) + "}";
        }
        throw new IllegalArgumentException("不支持的 JSON 类型: " + value.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String location) throws IOException {
        if (!(value instanceof Map<?, ?>)) throw new IOException(location + " 必须是 JSON 对象");
        return (Map<String, Object>) value;
    }

    private static final class Parser {
        private final String text;
        private int at;
        Parser(String text) { this.text = text == null ? "" : text; }
        boolean end() { return at >= text.length(); }
        void whitespace() { while (!end() && Character.isWhitespace(text.charAt(at))) at++; }
        Object value() throws IOException {
            whitespace();
            if (end()) throw error("缺少 JSON 值");
            return switch (text.charAt(at)) {
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true", true); case 'f' -> literal("false", false); case 'n' -> literal("null", null);
                default -> number();
            };
        }
        Map<String, Object> object() throws IOException {
            at++;
            var result = new LinkedHashMap<String, Object>();
            whitespace();
            if (take('}')) return result;
            while (true) {
                whitespace();
                if (end() || text.charAt(at) != '"') throw error("对象键必须是字符串");
                String key = string();
                whitespace();
                if (!take(':')) throw error("对象键后缺少冒号");
                result.put(key, value());
                whitespace();
                if (take('}')) return result;
                if (!take(',')) throw error("对象成员之间缺少逗号");
            }
        }
        List<Object> array() throws IOException {
            at++;
            var result = new ArrayList<>();
            whitespace();
            if (take(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (take(']')) return result;
                if (!take(',')) throw error("数组成员之间缺少逗号");
            }
        }
        String string() throws IOException {
            at++;
            var out = new StringBuilder();
            while (!end()) {
                char c = text.charAt(at++);
                if (c == '"') return out.toString();
                if (c < 0x20) throw error("字符串包含控制字符");
                if (c != '\\') { out.append(c); continue; }
                if (end()) throw error("字符串转义未结束");
                char e = text.charAt(at++);
                switch (e) {
                    case '"', '\\', '/' -> out.append(e); case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> { if (at + 4 > text.length()) throw error("Unicode 转义不完整");
                        try { out.append((char) Integer.parseInt(text.substring(at, at + 4), 16)); }
                        catch (NumberFormatException failure) { throw error("Unicode 转义无效"); } at += 4; }
                    default -> throw error("字符串转义无效");
                }
            }
            throw error("字符串未结束");
        }
        Object literal(String expected, Object value) throws IOException {
            if (!text.startsWith(expected, at)) throw error("JSON 字面量无效");
            at += expected.length(); return value;
        }
        Number number() throws IOException {
            int start = at;
            if (take('-') && end()) throw error("数字无效");
            while (!end() && Character.isDigit(text.charAt(at))) at++;
            if (!end() && text.charAt(at) == '.') { at++; while (!end() && Character.isDigit(text.charAt(at))) at++; }
            if (!end() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) { at++; if (!end() && (text.charAt(at) == '+' || text.charAt(at) == '-')) at++; while (!end() && Character.isDigit(text.charAt(at))) at++; }
            String value = text.substring(start, at);
            try { return value.contains(".") || value.contains("e") || value.contains("E") ? Double.parseDouble(value) : Long.parseLong(value); }
            catch (NumberFormatException failure) { throw error("数字无效"); }
        }
        boolean take(char c) { if (!end() && text.charAt(at) == c) { at++; return true; } return false; }
        IOException error(String message) { return new IOException(message + "（位置 " + at + "）"); }
    }
}

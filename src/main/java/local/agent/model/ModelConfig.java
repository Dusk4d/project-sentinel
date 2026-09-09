package local.agent.model;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public record ModelConfig(URI endpoint, String model, String apiKey, Duration timeout) {
    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    public static ModelConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static Optional<ModelConfig> optionalFromEnvironment() {
        return optionalFrom(System.getenv());
    }

    static Optional<ModelConfig> optionalFrom(Map<String, String> environment) {
        boolean hasBase = !environment.getOrDefault("SENTINEL_MODEL_BASE_URL", "").isBlank();
        boolean hasModel = !environment.getOrDefault("SENTINEL_MODEL_NAME", "").isBlank();
        if (!hasBase && !hasModel) return Optional.empty();
        return Optional.of(from(environment));
    }

    static ModelConfig from(Map<String, String> environment) {
        String base = required(environment, "SENTINEL_MODEL_BASE_URL");
        String model = required(environment, "SENTINEL_MODEL_NAME");
        URI endpoint = endpoint(base);
        String key = environment.getOrDefault("SENTINEL_MODEL_API_KEY", "").strip();
        if (!endpoint.getScheme().equalsIgnoreCase("https") && !isLoopback(endpoint.getHost())) {
            throw new IllegalArgumentException("远程模型地址必须使用 HTTPS；HTTP 只允许本机回环地址");
        }
        return new ModelConfig(endpoint, model, key, Duration.ofSeconds(timeoutSeconds(environment)));
    }

    private static URI endpoint(String base) {
        URI uri;
        try { uri = URI.create(base.strip()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("SENTINEL_MODEL_BASE_URL 不是有效 URL", e); }
        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("SENTINEL_MODEL_BASE_URL 必须是无用户信息和片段的 HTTP(S) URL");
        if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))
            throw new IllegalArgumentException("SENTINEL_MODEL_BASE_URL 只支持 HTTP(S)");
        String value = uri.toString().replaceAll("/+$", "");
        if (!value.endsWith("/chat/completions")) value += "/chat/completions";
        return URI.create(value);
    }

    private static boolean isLoopback(String host) {
        return host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1") || host.equals("[::1]");
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.getOrDefault(name, "").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("缺少环境变量 " + name);
        return value;
    }

    private static int timeoutSeconds(Map<String, String> environment) {
        String value = environment.getOrDefault("SENTINEL_MODEL_TIMEOUT_SECONDS", "").strip();
        if (value.isEmpty()) return DEFAULT_TIMEOUT_SECONDS;
        try {
            int seconds = Integer.parseInt(value);
            if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) throw new NumberFormatException();
            return seconds;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("SENTINEL_MODEL_TIMEOUT_SECONDS 必须是 1 到 300 的整数");
        }
    }
}

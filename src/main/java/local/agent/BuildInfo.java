package local.agent;

import java.io.IOException;
import java.util.Properties;

public final class BuildInfo {
    private static final String VERSION = loadVersion();

    private BuildInfo() { }

    public static String version() { return VERSION; }

    private static String loadVersion() {
        var properties = new Properties();
        try (var input = BuildInfo.class.getResourceAsStream("/project-sentinel.properties")) {
            if (input == null) throw new IllegalStateException("缺少构建版本资源 project-sentinel.properties");
            properties.load(input);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        String version = properties.getProperty("version", "").strip();
        if (version.isEmpty() || version.contains("${")) throw new IllegalStateException("构建版本资源未正确过滤");
        return version;
    }
}

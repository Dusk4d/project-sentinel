package local.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class WindowsLauncherContractTest {
    @Test void alwaysBuildsLatestJarAndStopsBeforeLaunchWhenBuildFails() throws Exception {
        String script = Files.readString(Path.of("start-web.cmd"), StandardCharsets.UTF_8);
        int build = script.indexOf("call mvnw.cmd --batch-mode --no-transfer-progress package");
        int failureGuard = script.indexOf("if errorlevel 1", build);
        int launch = script.indexOf("java -jar", failureGuard);

        assertTrue(build >= 0, "启动器必须在每次运行时执行增量构建");
        assertTrue(failureGuard > build, "启动器必须检查 Maven 退出码");
        assertTrue(launch > failureGuard, "只有成功构建后才能启动 JAR");
        assertFalse(script.contains("if not exist \"target\\workspace-agent-0.2.0.jar\""),
                "仅检查 JAR 是否存在会运行陈旧构件");
    }
}

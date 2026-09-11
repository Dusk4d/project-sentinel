package local.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class WindowsLauncherContractTest {
    @Test void mavenWrapperHandlesOrdinaryNonSymlinkMavenHome() throws Exception {
        String wrapper = Files.readString(Path.of("mvnw.cmd"), StandardCharsets.UTF_8);

        assertTrue(wrapper.contains("$null -eq $MAVEN_M2_ITEM.Target"),
                "Wrapper 必须先判断普通目录的空 Target，不能直接索引空值");
        assertFalse(wrapper.contains("(Get-Item $MAVEN_M2_PATH).Target[0]"),
                "直接索引空 Target 会令部分 Windows PowerShell 环境无法启动 Maven");
    }

    @Test void alwaysBuildsLatestJarAndStopsBeforeLaunchWhenBuildFails() throws Exception {
        String script = Files.readString(Path.of("start-web.cmd"), StandardCharsets.UTF_8);
        int build = script.indexOf("call mvnw.cmd --batch-mode --no-transfer-progress package");
        int failureGuard = script.indexOf("if errorlevel 1", build);
        int launch = script.indexOf("java -jar", failureGuard);

        assertTrue(build >= 0, "启动器必须在每次运行时执行增量构建");
        assertTrue(failureGuard > build, "启动器必须检查 Maven 退出码");
        assertTrue(launch > failureGuard, "只有成功构建后才能启动 JAR");
        assertTrue(script.contains("workspace-agent-*.jar"), "启动器必须动态发现 Maven 版本化构件");
        assertTrue(script.contains("if not defined SENTINEL_JAR"), "启动器必须拒绝缺失构件");
        assertTrue(script.contains("\"%~3\""), "启动器必须透传可选 Web Agent 状态目录");
        assertFalse(script.matches("(?s).*workspace-agent-\\d+\\.\\d+\\.\\d+\\.jar.*"),
                "启动器不得硬编码任何语义版本构件名");
    }
}

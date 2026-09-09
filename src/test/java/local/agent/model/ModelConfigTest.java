package local.agent.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ModelConfigTest {
    @Test void buildsChatCompletionsEndpointAndAllowsLocalHttpWithoutKey() {
        var config = ModelConfig.from(Map.of(
                "SENTINEL_MODEL_BASE_URL", "http://127.0.0.1:11434/v1/",
                "SENTINEL_MODEL_NAME", "local-model"));
        assertEquals("http://127.0.0.1:11434/v1/chat/completions", config.endpoint().toString());
        assertEquals("", config.apiKey());
        assertEquals(120, config.timeout().toSeconds());
    }

    @Test void requiresHttpsForRemoteHostsAndRequiredSettings() {
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.from(Map.of(
                "SENTINEL_MODEL_BASE_URL", "http://example.com/v1", "SENTINEL_MODEL_NAME", "demo")));
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.from(Map.of(
                "SENTINEL_MODEL_BASE_URL", "https://example.com/v1")));
    }

    @Test void optionalConfigurationIsAbsentOnlyWhenBothSettingsAreAbsent() {
        assertTrue(ModelConfig.optionalFrom(Map.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.optionalFrom(Map.of(
                "SENTINEL_MODEL_BASE_URL", "http://localhost:11434/v1")));
    }

    @Test void acceptsOnlyBoundedIntegerTimeout() {
        var environment = new java.util.HashMap<>(Map.of(
                "SENTINEL_MODEL_BASE_URL", "http://localhost:11434/v1",
                "SENTINEL_MODEL_NAME", "local-model"));
        environment.put("SENTINEL_MODEL_TIMEOUT_SECONDS", "180");
        assertEquals(180, ModelConfig.from(environment).timeout().toSeconds());
        for (String invalid : new String[]{"0", "301", "1.5", "slow"}) {
            environment.put("SENTINEL_MODEL_TIMEOUT_SECONDS", invalid);
            assertThrows(IllegalArgumentException.class, () -> ModelConfig.from(environment));
        }
    }
}

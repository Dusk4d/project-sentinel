package local.agent.state;

import java.time.Instant;

public record RunMetadata(long processId, Instant startedAt, String operation) { }

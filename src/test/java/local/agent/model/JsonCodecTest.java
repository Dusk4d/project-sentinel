package local.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class JsonCodecTest {
    @Test void parsesAndWritesNestedJsonWithEscapesAndNumbers() throws Exception {
        Object parsed = JsonCodec.parse("{\"text\":\"中\\n文\",\"items\":[1,true,null,{\"x\":-2.5e2}]}");
        var root = JsonCodec.object(parsed, "root");
        assertEquals("中\n文", root.get("text"));
        assertEquals(4, ((List<?>) root.get("items")).size());
        String written = JsonCodec.write(parsed);
        assertEquals(parsed, JsonCodec.parse(written));
    }

    @Test void rejectsTrailingContentAndMalformedStructures() {
        assertThrows(java.io.IOException.class, () -> JsonCodec.parse("{}x"));
        assertThrows(java.io.IOException.class, () -> JsonCodec.parse("[1,]"));
        assertThrows(java.io.IOException.class, () -> JsonCodec.parse("\"bad\\q\""));
    }

    @Test void writesMapsInStableInsertionOrder() {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("a", 1L); map.put("b", Map.of("c", false));
        assertEquals("{\"a\":1,\"b\":{\"c\":false}}", JsonCodec.write(map));
    }
}

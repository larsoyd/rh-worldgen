package rhworld.engine.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void parsesNestedValues() {
        Object v = Json.parse(" {\"a\": [1, -20, \"x\\\"y\\u00e9\"], \"b\": true, \"c\": null, \"d\": {}} ");
        Map<String, Object> want = new LinkedHashMap<>();
        want.put("a", List.of(BigInteger.ONE, BigInteger.valueOf(-20), "x\"y\u00e9"));
        want.put("b", true);
        want.put("c", null);
        want.put("d", Map.of());
        assertEquals(want, v);
    }

    @Test
    void keepsBigIntegersExact() {
        String big = "3".repeat(700);
        assertEquals(new BigInteger(big), Json.parse(big));
    }

    @Test
    void refusesFractionsAndTrailingInput() {
        assertThrows(JsonException.class, () -> Json.parse("1.5"));
        assertThrows(JsonException.class, () -> Json.parse("1e3"));
        assertThrows(JsonException.class, () -> Json.parse("[1] x"));
        assertThrows(JsonException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertThrows(JsonException.class, () -> Json.parse("[1,]"));
        assertThrows(JsonException.class, () -> Json.parse("01"));
    }

    @Test
    void writesCanonicalSortedForm() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("z", List.of(BigInteger.TWO, "a\nb"));
        m.put("a", Map.of("y", false, "x", 7L));
        assertEquals("{\"a\":{\"x\":7,\"y\":false},\"z\":[2,\"a\\nb\"]}", Json.write(m));
    }

    @Test
    void escapesLikePythonJsonWithRawUnicode() {
        assertEquals("\"\\b\\f\\n\\r\\t\\u0001\\u001f/\u00e9\u4e2d\\\"\\\\\"", Json.write("\b\f\n\r\t\u0001\u001f/\u00e9\u4e2d\"\\"));
    }

    @Test
    void roundTripsWrittenText() {
        String text = "{\"k\":[1,{\"q\":\"\\u0001\"}],\"n\":-5}";
        assertEquals(text, Json.write(Json.parse(text)));
    }
}

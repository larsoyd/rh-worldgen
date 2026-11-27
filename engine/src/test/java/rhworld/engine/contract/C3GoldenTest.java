package rhworld.engine.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.json.Json;
import rhworld.engine.telemetry.Telemetry;

class C3GoldenTest {
    @SuppressWarnings("unchecked")
    static Map<String, Object> golden() throws IOException {
        Path f = Path.of(System.getProperty("rhworld.samples", "missing"), "c3-golden.json");
        assertTrue(Files.isRegularFile(f), "c3 golden vectors missing at " + f);
        return (Map<String, Object>) Json.parse(Files.readString(f));
    }

    static List<BigInteger> layers(Object o) {
        return ((List<?>) o).stream().map(x -> new BigInteger((String) x)).toList();
    }

    static int[] ints(Object o) {
        return ((List<?>) o).stream().mapToInt(x -> ((BigInteger) x).intValueExact()).toArray();
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCaseMatchesTheOracle() throws IOException {
        Map<String, Object> g = golden();
        assertEquals("RHW-C3 v1", g.get("contract"));
        List<Map<String, Object>> cases = (List<Map<String, Object>>) g.get("cases");
        assertEquals(55, cases.size());
        int routes = 0;
        for (Map<String, Object> c : cases) {
            List<BigInteger> ls = layers(c.get("layers"));
            Verdict<C3.Basis> v = C3.basis(ls, Telemetry.NONE);
            if (!(v instanceof Verdict.Ok<C3.Basis> ok)) throw new AssertionError("layers=" + ls + " " + v);
            C3.Basis b = ok.value();
            String at = "layers=" + ls;
            assertArrayEquals(ints(c.get("primes")), b.primes(), at);
            assertArrayEquals(ints(c.get("vectors")), b.vectors(), at);
            assertEquals(((BigInteger) c.get("rank")).intValue(), b.rank(), at);
            assertEquals(((BigInteger) c.get("scanned")).longValue(), b.steps(), at);
            assertEquals(((BigInteger) c.get("budget")).longValue(), b.bound(), at);
            for (Object r : (List<Object>) c.get("routes")) {
                List<Object> route = (List<Object>) r;
                int from = ((BigInteger) route.get(0)).intValue(), to = ((BigInteger) route.get(1)).intValue();
                assertArrayEquals(ints(route.get(2)), b.route(from, to), at + " from=" + from + " to=" + to);
                routes++;
            }
        }
        assertTrue(routes >= 55 * 16, "routes=" + routes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyRefusalMatchesTheOracle() throws IOException {
        List<Map<String, Object>> refusals = (List<Map<String, Object>>) golden().get("refusals");
        assertEquals(9, refusals.size());
        for (Map<String, Object> r : refusals) {
            List<BigInteger> ls = layers(r.get("layers"));
            assertEquals("refused: " + r.get("reason"), C3.basis(ls, Telemetry.NONE).toString(), "layers=" + ls);
        }
    }
}

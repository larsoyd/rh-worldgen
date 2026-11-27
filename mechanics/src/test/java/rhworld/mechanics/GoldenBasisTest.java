package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Budget;
import rhworld.engine.json.Json;

class GoldenBasisTest {
    private static int integer(Object o) {
        return ((BigInteger) o).intValueExact();
    }

    // the oracle numbers bits by layer order and realms always put the smaller factor in bit 0
    private static Realm state(int v, boolean swapped) {
        return Realm.ofState(swapped ? (v & 1) << 1 | v >> 1 : v);
    }

    @Test
    void twoLayerCasesMatchTheOracle() throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(Path.of("..", "samples", "c3-golden.json")));
        assertEquals("RHW-C3 v1", root.get("contract"));
        int used = 0;
        for (Object o : (List<?>) root.get("cases")) {
            Map<?, ?> c = (Map<?, ?>) o;
            List<?> layers = (List<?>) c.get("layers");
            if (layers.size() != 2) continue;
            BigInteger first = new BigInteger((String) layers.get(0));
            Warden warden = new Warden(first, new BigInteger((String) layers.get(1)));
            boolean swapped = first.compareTo(new BigInteger((String) layers.get(1))) > 0;
            WildGate gate = WildGate.forModulus(warden.modulus());
            String tag = "layers=" + layers;
            assertEquals(integer(c.get("budget")), Budget.forBits(warden.modulus().bitLength()), tag);
            assertEquals(integer(c.get("alphabet_size")), gate.size(), tag);
            assertEquals(integer(c.get("rank")), warden.rank(gate.primes()), tag);
            List<Integer> kept = new ArrayList<>();
            List<Realm> span = new ArrayList<>(List.of(Realm.HEARTH));
            for (int l : gate.primes()) {
                Realm s = warden.realm(BigInteger.valueOf(l));
                if (span.contains(s)) continue;
                kept.add(l);
                for (Realm a : List.copyOf(span)) span.add(a.plus(s));
                if (kept.size() == 2) break;
            }
            List<?> primes = (List<?>) c.get("primes");
            List<?> vectors = (List<?>) c.get("vectors");
            for (int j = 0; j < 2; j++) {
                assertEquals(integer(primes.get(j)), kept.get(j), tag);
                assertEquals(state(integer(vectors.get(j)), swapped), warden.realm(BigInteger.valueOf(kept.get(j))), tag);
            }
            for (Object r : (List<?>) c.get("routes")) {
                List<?> route = (List<?>) r;
                Realm shift = Realm.HEARTH;
                for (Object l : (List<?>) route.get(2)) shift = shift.plus(warden.realm((BigInteger) l));
                assertEquals(state(integer(route.get(1)), swapped), state(integer(route.get(0)), swapped).plus(shift), tag + " route=" + route);
            }
            used++;
        }
        System.out.println("[golden] contract=RHW-C3 two_layer_cases=" + used);
        assertTrue(used >= 40, "two layer cases=" + used);
    }

    @Test
    void stateBitsFollowTheOracleEncoding() {
        for (Realm r : Realm.values()) assertEquals(r, Realm.ofState(r.state()));
        assertEquals(1, Realm.FROST.state());
        assertEquals(2, Realm.MIST.state());
        assertEquals(3, Realm.EMBER.state());
    }
}

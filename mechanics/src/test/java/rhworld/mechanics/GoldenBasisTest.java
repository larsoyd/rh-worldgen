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

    @Test
    void twoLayerCasesMatchTheOracle() throws IOException {
        Map<?, ?> root = (Map<?, ?>) Json.parse(Files.readString(Path.of("..", "samples", "c3-golden.json")));
        assertEquals("RHW-C3 v1", root.get("contract"));
        int used = 0;
        for (Object o : (List<?>) root.get("cases")) {
            Map<?, ?> c = (Map<?, ?>) o;
            List<?> layers = (List<?>) c.get("layers");
            if (layers.size() != 2) continue;
            Warden warden = new Warden(new BigInteger((String) layers.get(0)), new BigInteger((String) layers.get(1)));
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
                assertEquals(Realm.ofState(integer(vectors.get(j))), warden.realm(BigInteger.valueOf(kept.get(j))), tag);
            }
            for (Object r : (List<?>) c.get("routes")) {
                List<?> route = (List<?>) r;
                Realm shift = Realm.HEARTH;
                for (Object l : (List<?>) route.get(2)) shift = shift.plus(warden.realm((BigInteger) l));
                assertEquals(Realm.ofState(integer(route.get(1))), Realm.ofState(integer(route.get(0))).plus(shift), tag + " route=" + route);
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

package rhworld.engine.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import rhworld.engine.Props;
import rhworld.engine.arith.PocklingtonAccess;
import rhworld.engine.contract.Verdict;
import rhworld.engine.json.Json;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.GmWorld;
import rhworld.engine.world.PublicWorld;

class CertificateMutationTest {
    record Leaf(List<Object> path, String reported) {}

    static String provedCertificate() {
        BigInteger p = PocklingtonAccess.smoothPrime(700), q = PocklingtonAccess.smoothPrime(710);
        PublicWorld w = CertificateTest.ok(PublicWorld.open(p.multiply(q), Telemetry.NONE));
        GmWorld.Proofs proofs = new GmWorld.Proofs(PocklingtonAccess.certificate(p), PocklingtonAccess.certificate(q));
        return Certificate.of(CertificateTest.ok(GmWorld.reveal(w, p, Optional.of(proofs), Telemetry.NONE))).json();
    }

    static void leaves(Object node, List<Object> path, String reported, boolean inList, List<Leaf> out) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                List<Object> p = new ArrayList<>(path);
                p.add(e.getKey());
                String r = inList ? reported : (reported.isEmpty() ? "" : reported + ".") + e.getKey();
                leaves(e.getValue(), p, r, inList, out);
            }
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                List<Object> p = new ArrayList<>(path);
                p.add(i);
                leaves(l.get(i), p, reported, true, out);
            }
            if (l.isEmpty()) out.add(new Leaf(path, reported));
        } else {
            out.add(new Leaf(path, reported));
        }
    }

    @SuppressWarnings("unchecked")
    static Object deepCopy(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> c = new LinkedHashMap<>();
            m.forEach((k, v) -> c.put((String) k, deepCopy(v)));
            return c;
        }
        if (o instanceof List<?> l) return new ArrayList<>(l.stream().map(CertificateMutationTest::deepCopy).toList());
        return o;
    }

    static Object mutate(Object v, SplittableRandom r) {
        return switch (v) {
            case null -> "x";
            case BigInteger b -> b.add(BigInteger.valueOf(1 + r.nextInt(5)));
            case Boolean b -> !b;
            case String s when s.matches("[0-9]+") -> new BigInteger(s).add(BigInteger.TWO).toString();
            case String s -> s + (char) ('a' + r.nextInt(26));
            case List<?> l -> List.of("x");
            default -> throw new IllegalStateException("leaf " + v);
        };
    }

    @SuppressWarnings("unchecked")
    static String withLeaf(String json, List<Object> path, SplittableRandom r) {
        Object root = deepCopy(Json.parse(json));
        Object node = root;
        for (int i = 0; i < path.size() - 1; i++) {
            Object k = path.get(i);
            node = k instanceof Integer idx ? ((List<Object>) node).get(idx) : ((Map<String, Object>) node).get(k);
        }
        Object last = path.get(path.size() - 1);
        if (last instanceof Integer idx) ((List<Object>) node).set(idx, mutate(((List<Object>) node).get(idx), r));
        else ((Map<String, Object>) node).put((String) last, mutate(((Map<String, Object>) node).get(last), r));
        return Json.write(root);
    }

    static boolean contractOwned(String reported) {
        return reported.equals("header.params.n") || reported.equals("deferred.reveal.p") || reported.startsWith("deferred.reveal.proofs");
    }

    static int sweep(String json) {
        List<Leaf> all = new ArrayList<>();
        leaves(Json.parse(json), List.of(), "", false, all);
        SplittableRandom r = new SplittableRandom(Props.SEED);
        int refused = 0;
        for (Leaf leaf : all) {
            String reason = Certificate.verify(withLeaf(json, leaf.path(), r), Telemetry.NONE).toString();
            String at = "leaf=" + leaf.path() + " reason=" + reason + " seed=" + Props.SEED;
            if (leaf.reported().equals("engine_version")) {
                assertTrue(reason.startsWith("Ok["), at);
                continue;
            }
            assertTrue(reason.startsWith("refused: "), at);
            if (leaf.reported().equals("header.schema")) assertEquals("refused: unsupported schema", reason, at);
            else if (leaf.reported().equals("deferred.reveal")) assertEquals("refused: certificate malformed", reason, at);
            else if (!contractOwned(leaf.reported())) assertEquals("refused: field mismatch " + leaf.reported(), reason, at);
            refused++;
        }
        return refused;
    }

    @Test
    void everyLeafOfARevealedCertificateIsRefusedByName() {
        int refused = sweep(Certificate.of(CertificateTest.gm(3)).json());
        assertTrue(refused >= 60, "refused=" + refused);
    }

    @Test
    void everyLeafOfAPublicCertificateIsRefusedByName() {
        int refused = sweep(Certificate.of(CertificateTest.world(5)).json());
        assertTrue(refused >= 40, "refused=" + refused);
    }

    @Test
    void everyLeafOfAProvedCertificateIsRefused() {
        int refused = sweep(provedCertificate());
        assertTrue(refused >= 70, "refused=" + refused);
    }

    @Test
    void certificatesRoundTripThroughTheParserForManyModuli() {
        List<Map<String, Object>> cases = rhworld.engine.Golden.list("modulus");
        int checked = 0;
        for (int i = 0; i < 8; i++) {
            String pub = Certificate.of(CertificateTest.world(i)).json(), gm = Certificate.of(CertificateTest.gm(i)).json();
            for (String json : List.of(pub, gm)) {
                assertEquals(json, Json.write(Json.parse(json)));
                assertTrue(Certificate.verify(json, Telemetry.NONE) instanceof Verdict.Ok<?>, cases.get(i).get("label") + "");
                checked++;
            }
        }
        assertEquals(16, checked);
    }
}

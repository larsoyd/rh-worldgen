package rhworld.engine.cert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import rhworld.engine.Golden;
import rhworld.engine.contract.Verdict;
import rhworld.engine.json.Json;
import rhworld.engine.telemetry.Telemetry;
import rhworld.engine.world.GmWorld;
import rhworld.engine.world.PublicWorld;

class CertificateTest {
    static <T> T ok(Verdict<T> v) {
        if (v instanceof Verdict.Ok<T> o) return o.value();
        throw new AssertionError(v.toString());
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> golden(int i) {
        return Golden.<Map<String, Object>>list("modulus").get(i);
    }

    static PublicWorld world(int i) {
        return ok(PublicWorld.open(new BigInteger((String) golden(i).get("n")), Telemetry.NONE));
    }

    @SuppressWarnings("unchecked")
    static GmWorld gm(int i) {
        BigInteger f = new BigInteger(((List<String>) golden(i).get("factors")).get(1));
        return ok(GmWorld.reveal(world(i), f, Optional.empty(), Telemetry.NONE));
    }

    @SuppressWarnings("unchecked")
    static String edit(String json, String path, UnaryOperator<Object> f) {
        Map<String, Object> root = deepCopy((Map<String, Object>) Json.parse(json));
        String[] parts = path.split("\\.");
        Map<String, Object> m = root;
        for (int i = 0; i < parts.length - 1; i++) m = (Map<String, Object>) m.get(parts[i]);
        m.put(parts[parts.length - 1], f.apply(m.get(parts[parts.length - 1])));
        return Json.write(root);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, v instanceof Map<?, ?> x ? deepCopy((Map<String, Object>) x) : v));
        return out;
    }

    @Test
    void publicCertificateHasTheFourSectionsAndVerifies() {
        String json = Certificate.of(world(0)).json();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(json);
        assertEquals(List.of("deferred", "header", "not_proved", "recomputed", "theorem"), m.keySet().stream().sorted().toList());
        assertEquals(json, Json.write(Json.parse(json)));
        Certificate.Report r = ok(Certificate.verify(json, Telemetry.NONE));
        assertEquals(Certificate.of(world(0)).worldId(), r.worldId());
        assertEquals(false, r.revealed());
        assertTrue(json.contains("\"claim_scope\":\"prefix 1..B\""), json);
        assertTrue(json.contains("GRH is not checked by this certificate"), json);
        assertTrue(json.contains("\"primality_test\":\"mr25-fixed-bases\""), json);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recomputedSectionHoldsTheOracleValues() {
        for (int i : new int[] {0, 3, 7}) {
            Map<String, Object> c = golden(i);
            Map<String, Object> m = (Map<String, Object>) Json.parse(Certificate.of(world(i)).json());
            Map<String, Object> rec = (Map<String, Object>) m.get("recomputed");
            Map<String, Object> c1 = (Map<String, Object>) rec.get("c1");
            long side = ((BigInteger) c.get("side")).longValue(), g2 = ((BigInteger) c.get("g2")).longValue();
            assertEquals(c.get("budget"), c1.get("budget"));
            assertEquals(BigInteger.valueOf(side * side), c1.get("trial_limit"));
            assertEquals(BigInteger.valueOf(new BigInteger((String) c.get("n")).bitLength()), c1.get("bits"));
            assertEquals(Map.of("witness", BigInteger.valueOf(g2), "steps", BigInteger.valueOf(g2), "bound", c.get("budget")), c1.get("g2"));
            Map<String, Object> basis = (Map<String, Object>) ((Map<String, Object>) rec.get("c3")).get("basis");
            assertEquals(List.of(BigInteger.valueOf(g2)), basis.get("primes"));
            assertEquals(List.of(BigInteger.ONE), basis.get("vectors"));
            assertEquals(c.get("n"), ((Map<String, Object>) ((Map<String, Object>) m.get("header")).get("params")).get("n"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void worldIdIsTheHashOfHeaderAndRecomputed() throws Exception {
        Certificate cert = Certificate.of(world(5));
        Map<String, Object> m = (Map<String, Object>) Json.parse(cert.json());
        String text = "{\"header\":" + Json.write(m.get("header")) + ",\"recomputed\":" + Json.write(m.get("recomputed")) + "}";
        byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(java.util.HexFormat.of().formatHex(d), cert.worldId());
    }

    @Test
    void addedAndRemovedFieldsAreNamed() {
        String pub = Certificate.of(world(0)).json();
        assertEquals("refused: field mismatch recomputed.extra",
                Certificate.verify(edit(pub, "recomputed.extra", v -> "x"), Telemetry.NONE).toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> m = deepCopy((Map<String, Object>) Json.parse(pub));
        @SuppressWarnings("unchecked")
        Map<String, Object> c1 = (Map<String, Object>) ((Map<String, Object>) m.get("recomputed")).get("c1");
        c1.remove("side");
        assertEquals("refused: field mismatch recomputed.c1.side", Certificate.verify(Json.write(m), Telemetry.NONE).toString());
    }

    @Test
    void sameModulusGivesIdenticalBytes() {
        assertEquals(Certificate.of(world(2)).json(), Certificate.of(world(2)).json());
        assertEquals(64, Certificate.of(world(2)).worldId().length());
        assertNotEquals(Certificate.of(world(1)).worldId(), Certificate.of(world(2)).worldId());
    }

    @Test
    void worldIdIgnoresTheDeferredSection() {
        assertEquals(Certificate.of(world(3)).worldId(), Certificate.of(gm(3)).worldId());
        assertNotEquals(Certificate.of(world(3)).json(), Certificate.of(gm(3)).json());
    }

    @Test
    void revealedCertificateVerifies() {
        String json = Certificate.of(gm(3)).json();
        Certificate.Report r = ok(Certificate.verify(json, Telemetry.NONE));
        assertEquals(true, r.revealed());
        assertEquals("mr25-probable", r.factorsStatus());
        assertTrue(json.contains("\"factors_status\":\"mr25-probable\""), json);
    }

    @Test
    void provedRevealRoundTripsAndABrokenProofIsRefused() {
        BigInteger p = rhworld.engine.arith.PocklingtonAccess.smoothPrime(700), q = rhworld.engine.arith.PocklingtonAccess.smoothPrime(710);
        PublicWorld w = ok(PublicWorld.open(p.multiply(q), Telemetry.NONE));
        GmWorld.Proofs proofs = new GmWorld.Proofs(rhworld.engine.arith.PocklingtonAccess.certificate(p), rhworld.engine.arith.PocklingtonAccess.certificate(q));
        String json = Certificate.of(ok(GmWorld.reveal(w, p, Optional.of(proofs), Telemetry.NONE))).json();
        assertEquals("proved", ok(Certificate.verify(json, Telemetry.NONE)).factorsStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> m = deepCopy((Map<String, Object>) Json.parse(json));
        @SuppressWarnings("unchecked")
        Map<String, Object> qProof = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) m.get("deferred")).get("reveal")).get("proofs")).get("q");
        qProof.put("witnesses", Map.of("2", BigInteger.ONE, "3", BigInteger.ONE));
        assertEquals("refused: proof for q: witness fails for factor 2", Certificate.verify(Json.write(m), Telemetry.NONE).toString());
    }

    @Test
    void everyTamperedFieldIsNamed() {
        String pub = Certificate.of(world(0)).json(), gm = Certificate.of(gm(3)).json();
        assertEquals("refused: field mismatch recomputed.c1.g2.witness",
                Certificate.verify(edit(pub, "recomputed.c1.g2.witness", v -> ((BigInteger) v).add(BigInteger.ONE)), Telemetry.NONE).toString());
        assertEquals("refused: field mismatch recomputed.c1.budget",
                Certificate.verify(edit(pub, "recomputed.c1.budget", v -> ((BigInteger) v).add(BigInteger.ONE)), Telemetry.NONE).toString());
        assertEquals("refused: field mismatch theorem.RHW-C1.claim_scope",
                Certificate.verify(edit(pub, "theorem.RHW-C1.claim_scope", v -> "anywhere"), Telemetry.NONE).toString());
        assertEquals("refused: field mismatch deferred.reveal.factors_status",
                Certificate.verify(edit(gm, "deferred.reveal.factors_status", v -> "proved"), Telemetry.NONE).toString());
        assertEquals("refused: field mismatch deferred.reveal.g1.p.witness",
                Certificate.verify(edit(gm, "deferred.reveal.g1.p.witness", v -> BigInteger.valueOf(999)), Telemetry.NONE).toString());
        assertEquals("refused: field mismatch not_proved",
                Certificate.verify(edit(pub, "not_proved", v -> List.of()), Telemetry.NONE).toString());
        assertEquals("refused: unsupported schema",
                Certificate.verify(edit(pub, "header.schema", v -> "rhworld.cert/2"), Telemetry.NONE).toString());
    }

    @Test
    void modulusChangeIsRefusedByTheContractNotBlindlyRecomputed() {
        String pub = Certificate.of(world(0)).json();
        assertEquals("refused: small factor", Certificate.verify(edit(pub, "header.params.n",
                v -> new BigInteger((String) v).multiply(BigInteger.valueOf(3)).toString()), Telemetry.NONE).toString());
        String other = edit(pub, "header.params.n", v -> (String) golden(1).get("n"));
        assertTrue(Certificate.verify(other, Telemetry.NONE).toString().startsWith("refused: field mismatch "), "other n");
    }

    @Test
    void malformedInputIsRefused() {
        assertEquals("refused: certificate malformed", Certificate.verify("[1,2]", Telemetry.NONE).toString());
        assertEquals("refused: unsupported schema", Certificate.verify("{\"header\":{}}", Telemetry.NONE).toString());
        assertEquals("refused: certificate malformed", Certificate.verify("{\"header\":{\"schema\":\"rhworld.cert/1\"}}", Telemetry.NONE).toString());
        assertEquals("refused: certificate malformed", Certificate.verify("{", Telemetry.NONE).toString());
    }

    @Test
    void wrongRevealedFactorIsRefusedByTheRevealCheck() {
        String gm = Certificate.of(gm(3)).json();
        assertEquals("refused: revealed factor does not divide n",
                Certificate.verify(edit(gm, "deferred.reveal.p", v -> "1000003"), Telemetry.NONE).toString());
    }
}

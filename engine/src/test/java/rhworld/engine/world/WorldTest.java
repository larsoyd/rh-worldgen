package rhworld.engine.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import rhworld.engine.Golden;
import rhworld.engine.arith.Arith;
import rhworld.engine.contract.Verdict;
import rhworld.engine.telemetry.Telemetry;

class WorldTest {
    static <T> T ok(Verdict<T> v) {
        if (v instanceof Verdict.Ok<T> o) return o.value();
        throw new AssertionError(v.toString());
    }

    static List<Map<String, Object>> accepted() {
        return Golden.<Map<String, Object>>list("modulus").stream().filter(c -> !c.containsKey("refused")).toList();
    }

    static BigInteger big(Object o) {
        return new BigInteger((String) o);
    }

    @SuppressWarnings("unchecked")
    static BigInteger factor(Map<String, Object> c, int i) {
        return big(((List<String>) c.get("factors")).get(i));
    }

    @Test
    void publicWorldMatchesOracle() {
        for (Map<String, Object> c : accepted()) {
            PublicWorld w = ok(PublicWorld.open(big(c.get("n")), Telemetry.NONE));
            assertEquals(((BigInteger) c.get("g2")).longValue(), w.g2().value());
            assertEquals(((BigInteger) c.get("budget")).longValue(), w.budget());
            assertEquals(w.g2().value(), w.basis().primes()[0]);
            assertEquals(-1, w.jacobi(BigInteger.valueOf(w.g2().value())));
        }
        for (Map<String, Object> c : Golden.<Map<String, Object>>list("modulus")) {
            if (c.containsKey("refused")) assertEquals(c.get("refused"), PublicWorld.open(big(c.get("n")), Telemetry.NONE).toString());
        }
    }

    @Test
    void revealTakesEitherFactorAndCanonicalisesToTheSmaller() {
        for (Map<String, Object> c : accepted()) {
            PublicWorld w = ok(PublicWorld.open(big(c.get("n")), Telemetry.NONE));
            BigInteger a = factor(c, 0), b = factor(c, 1);
            GmWorld g1 = ok(GmWorld.reveal(w, a, Optional.empty(), Telemetry.NONE));
            GmWorld g2 = ok(GmWorld.reveal(w, b, Optional.empty(), Telemetry.NONE));
            assertEquals(a.min(b), g1.p());
            assertEquals(a.max(b), g2.q());
            assertEquals("mr25-probable", g1.factorsStatus());
            @SuppressWarnings("unchecked")
            List<BigInteger> want = (List<BigInteger>) c.get("g1");
            boolean swapped = a.compareTo(b) > 0;
            assertEquals(want.get(swapped ? 1 : 0).longValue(), g1.g1p().value());
            assertEquals(want.get(swapped ? 0 : 1).longValue(), g1.g1q().value());
            assertEquals(2, g1.hiddenBasis().rank());
        }
    }

    @Test
    void realmBitsMultiplyToThePublicSymbol() {
        Map<String, Object> c = accepted().get(3);
        PublicWorld w = ok(PublicWorld.open(big(c.get("n")), Telemetry.NONE));
        GmWorld g = ok(GmWorld.reveal(w, factor(c, 0), Optional.empty(), Telemetry.NONE));
        assertEquals(new Realm(1, 1), ok(g.realm(BigInteger.ONE)));
        int[] seen = new int[4];
        for (long x = 2; x < 3000; x++) {
            Realm r = ok(g.realm(BigInteger.valueOf(x)));
            assertEquals(w.jacobi(BigInteger.valueOf(x)), r.p() * r.q(), "x=" + x);
            assertEquals(Arith.jacobi(BigInteger.valueOf(x), g.p()), r.p(), "x=" + x);
            seen[r.state()]++;
        }
        for (int s = 0; s < 4; s++) assertTrue(seen[s] > 0, "state " + s);
        assertEquals(ok(g.realm(BigInteger.valueOf(7))), ok(g.realm(BigInteger.valueOf(7).add(w.n()))));
        assertEquals("refused: not a unit", g.realm(g.p().multiply(BigInteger.valueOf(5))).toString());
        assertEquals("refused: not a unit", g.realm(BigInteger.ZERO).toString());
        assertEquals("refused: not a unit", g.realm(g.q().multiply(BigInteger.valueOf(5))).toString());
    }

    @Test
    void revealRefusals() {
        Map<String, Object> c = accepted().get(0);
        PublicWorld w = ok(PublicWorld.open(big(c.get("n")), Telemetry.NONE));
        assertEquals("refused: revealed factor does not divide n", GmWorld.reveal(w, BigInteger.valueOf(1_000_003), Optional.empty(), Telemetry.NONE).toString());
        assertEquals("refused: revealed factor does not divide n", GmWorld.reveal(w, BigInteger.ONE, Optional.empty(), Telemetry.NONE).toString());
        assertEquals("refused: revealed factor does not divide n", GmWorld.reveal(w, w.n(), Optional.empty(), Telemetry.NONE).toString());
        BigInteger p = factor(c, 0);
        PublicWorld cube = ok(PublicWorld.open(p.pow(3), Telemetry.NONE));
        assertEquals("refused: cofactor not prime", GmWorld.reveal(cube, p, Optional.empty(), Telemetry.NONE).toString());
        assertEquals("refused: revealed factor not prime", GmWorld.reveal(cube, p.multiply(p), Optional.empty(), Telemetry.NONE).toString());
    }

    @Test
    void pocklingtonCertificatesMarkFactorsProved() {
        BigInteger p = rhworld.engine.arith.PocklingtonAccess.smoothPrime(700), q = rhworld.engine.arith.PocklingtonAccess.smoothPrime(710);
        assertTrue(p.multiply(q).bitLength() >= 1024, "bits=" + p.multiply(q).bitLength());
        PublicWorld w = ok(PublicWorld.open(p.multiply(q), Telemetry.NONE));
        GmWorld.Proofs proofs = new GmWorld.Proofs(rhworld.engine.arith.PocklingtonAccess.certificate(p), rhworld.engine.arith.PocklingtonAccess.certificate(q));
        GmWorld g = ok(GmWorld.reveal(w, q, Optional.of(proofs), Telemetry.NONE));
        assertEquals("proved", g.factorsStatus());
        GmWorld.Proofs badQ = new GmWorld.Proofs(proofs.p(), proofs.p());
        assertTrue(GmWorld.reveal(w, q, Optional.of(badQ), Telemetry.NONE).toString().startsWith("refused: proof for q: "));
        GmWorld.Proofs swapped = new GmWorld.Proofs(proofs.q(), proofs.p());
        assertEquals("refused: proof for p: factored part does not divide n-1", GmWorld.reveal(w, q, Optional.of(swapped), Telemetry.NONE).toString());
    }

    @Test
    void sharedWorldsGiveTheSameAnswersFromManyThreads() throws Exception {
        Map<String, Object> c = accepted().get(4);
        PublicWorld w = ok(PublicWorld.open(big(c.get("n")), Telemetry.NONE));
        GmWorld g = ok(GmWorld.reveal(w, factor(c, 1), Optional.empty(), Telemetry.NONE));
        int[] want = new int[4000];
        for (int x = 1; x < want.length; x++) want[x] = g.realm(BigInteger.valueOf(x)) instanceof Verdict.Ok<Realm> o ? o.value().state() : -1;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> jobs = new ArrayList<>();
            for (int t = 0; t < 64; t++) {
                int seed = t;
                jobs.add(pool.submit(() -> {
                    int bad = 0;
                    for (int i = 0; i < want.length; i++) {
                        int x = 1 + (i * 7919 + seed * 104729) % (want.length - 1);
                        int got = g.realm(BigInteger.valueOf(x)) instanceof Verdict.Ok<Realm> o ? o.value().state() : -1;
                        if (got != want[x]) bad++;
                        if (w.basis().primes()[0] != w.g2().value()) bad++;
                    }
                    return bad;
                }));
            }
            int bad = 0;
            for (Future<Integer> f : jobs) bad += f.get(60, TimeUnit.SECONDS);
            assertEquals(0, bad);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void basisArraysCannotBeMutatedFromOutside() {
        PublicWorld w = ok(PublicWorld.open(big(accepted().get(0).get("n")), Telemetry.NONE));
        int first = w.basis().primes()[0];
        w.basis().primes()[0] = 999;
        assertEquals(first, w.basis().primes()[0]);
    }
}

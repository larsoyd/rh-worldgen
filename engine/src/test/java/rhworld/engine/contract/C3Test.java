package rhworld.engine.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.Golden;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.engine.telemetry.Telemetry;

class C3Test {
    private static BigInteger big(Object o) {
        return new BigInteger((String) o);
    }

    private static C3.Basis ok(Verdict<C3.Basis> v) {
        if (v instanceof Verdict.Ok<C3.Basis> o) return o.value();
        throw new AssertionError(v.toString());
    }

    private static int vector(long l, List<BigInteger> layers) {
        int v = 0;
        for (int i = 0; i < layers.size(); i++) if (Arith.jacobi(l, layers.get(i)) == -1) v |= 1 << i;
        return v;
    }

    @Test
    void publicBasisIsTheG2Witness() {
        for (Map<String, Object> c : Golden.<Map<String, Object>>list("modulus")) {
            if (c.containsKey("refused")) continue;
            C3.Basis b = ok(C3.basis(List.of(big(c.get("n"))), Telemetry.NONE));
            assertEquals(1, b.rank());
            assertEquals(((BigInteger) c.get("g2")).intValue(), b.primes()[0], (String) c.get("label"));
        }
    }

    @Test
    void hiddenBasisHasRankTwoAndStartsAtTheSmallerG1Witness() {
        for (Map<String, Object> c : Golden.<Map<String, Object>>list("modulus")) {
            if (c.containsKey("refused")) continue;
            @SuppressWarnings("unchecked")
            List<String> f = (List<String>) c.get("factors");
            @SuppressWarnings("unchecked")
            List<BigInteger> g1 = (List<BigInteger>) c.get("g1");
            List<BigInteger> layers = List.of(big(f.get(0)), big(f.get(1)));
            C3.Basis b = ok(C3.basis(layers, Telemetry.NONE));
            assertEquals(2, b.rank(), (String) c.get("label"));
            assertEquals(Math.min(g1.get(0).intValue(), g1.get(1).intValue()), b.primes()[0], (String) c.get("label"));
            for (int j = 0; j < 2; j++) assertEquals(vector(b.primes()[j], layers), b.vectors()[j]);
            assertTrue(b.vectors()[0] != b.vectors()[1] && b.vectors()[1] != 0);
        }
    }

    @Test
    void basisIsGreedyLeastPrimesOnSmallLayers() {
        int checked = 0;
        for (long p = 3001; p < 3400 && checked < 60; p += 2) {
            if (!Arith.isProbablePrime(BigInteger.valueOf(p))) continue;
            for (long q = p + 2; q < p + 300; q += 2) {
                if (!Arith.isProbablePrime(BigInteger.valueOf(q))) continue;
                List<BigInteger> layers = List.of(BigInteger.valueOf(p), BigInteger.valueOf(q), BigInteger.valueOf(p * q));
                Verdict<C3.Basis> v = C3.basis(layers, Telemetry.NONE);
                assertEquals("refused: dependent layers", v.toString());
                layers = List.of(BigInteger.valueOf(p), BigInteger.valueOf(q));
                C3.Basis b = ok(C3.basis(layers, Telemetry.NONE));
                int span = 1, kept = 0;
                long steps = 0;
                for (long l = 2; kept < 2; l++) {
                    if (!Arith.isProbablePrime(BigInteger.valueOf(l))) continue;
                    steps++;
                    int x = vector(l, layers);
                    if ((span >>> x & 1) == 0) {
                        assertEquals(l, b.primes()[kept], "p=" + p + " q=" + q);
                        int next = span;
                        for (int s = 0; s < 4; s++) if ((span >>> s & 1) == 1) next |= 1 << (s ^ x);
                        span = next;
                        kept++;
                    }
                }
                assertEquals(steps, b.steps(), "p=" + p + " q=" + q);
                checked++;
                break;
            }
        }
        assertTrue(checked >= 40, "checked=" + checked);
    }

    @Test
    void greedyBasisMatchesBruteForceSpanForThreeAndFourLayers() {
        long[] ps = {3001, 3011, 3019, 3023, 3037, 3041, 3049, 3061, 3067, 3079, 3083, 3089, 3109, 3119, 3121, 3137};
        int checked = 0;
        for (int start = 0; start + 4 <= ps.length; start++) {
            for (int r = 3; r <= 4; r++) {
                List<BigInteger> layers = new java.util.ArrayList<>();
                for (int i = 0; i < r; i++) layers.add(BigInteger.valueOf(ps[start + i]));
                C3.Basis b = ok(C3.basis(layers, Telemetry.NONE));
                long span = 1;
                int kept = 0;
                for (long l = 2; kept < r; l++) {
                    if (!Arith.isProbablePrime(BigInteger.valueOf(l))) continue;
                    int x = vector(l, layers);
                    if ((span >>> x & 1) == 0) {
                        assertEquals(l, b.primes()[kept], "layers=" + layers);
                        long next = span;
                        for (int s = 0; s < 1 << r; s++) if ((span >>> s & 1) == 1) next |= 1L << (s ^ x);
                        span = next;
                        kept++;
                    }
                }
                assertEquals(r, b.rank());
                for (int to = 0; to < 1 << r; to++) {
                    int st = 0;
                    for (int l : b.route(0, to)) st ^= vector(l, layers);
                    assertEquals(to, st, "layers=" + layers + " to=" + to);
                }
                checked++;
            }
        }
        assertEquals(26, checked);
    }

    @Test
    void routesReachEveryStateInAtMostRankHops() {
        Map<String, Object> c = Golden.<Map<String, Object>>list("modulus").get(3);
        @SuppressWarnings("unchecked")
        List<String> f = (List<String>) c.get("factors");
        List<BigInteger> layers = List.of(big(f.get(0)), big(f.get(1)));
        C3.Basis b = ok(C3.basis(layers, Telemetry.NONE));
        for (int from = 0; from < 4; from++) {
            for (int to = 0; to < 4; to++) {
                int[] hops = b.route(from, to);
                assertTrue(hops.length <= 2);
                int s = from;
                for (int l : hops) s ^= vector(l, layers);
                assertEquals(to, s, "from=" + from + " to=" + to);
                if (from == to) assertEquals(0, hops.length);
            }
        }
    }

    @Test
    void preconditionsRefuseInOrder() {
        BigInteger p = BigInteger.TWO.pow(521).subtract(BigInteger.ONE);
        BigInteger q = BigInteger.TWO.pow(607).subtract(BigInteger.ONE);
        assertEquals("refused: no layers", C3.basis(List.of(), Telemetry.NONE).toString());
        assertEquals("refused: too many layers", C3.basis(java.util.Collections.nCopies(9, p), Telemetry.NONE).toString());
        assertEquals("refused: modulus too small or even", C3.basis(List.of(p, BigInteger.TWO.multiply(q)), Telemetry.NONE).toString());
        assertEquals("refused: modulus too small or even", C3.basis(List.of(BigInteger.ONE), Telemetry.NONE).toString());
        assertEquals("refused: dependent layers", C3.basis(List.of(p, q.multiply(q)), Telemetry.NONE).toString());
        assertEquals("refused: small factor", C3.basis(List.of(p, q.multiply(BigInteger.valueOf(7))), Telemetry.NONE).toString());
        assertEquals("refused: modulus too small or even", C3.basis(List.of(BigInteger.valueOf(2999)), Telemetry.NONE).toString());
    }

    @Test
    void trialDivisionBoundUsesTheLcmBitLength() {
        BigInteger p = BigInteger.TWO.pow(521).subtract(BigInteger.ONE);
        BigInteger q = BigInteger.TWO.pow(607).subtract(BigInteger.ONE);
        C3.Basis b = ok(C3.basis(List.of(p, p.multiply(q)), Telemetry.NONE));
        assertEquals(Budget.forBits(p.multiply(q).bitLength()), b.bound());
        assertEquals(Budget.forBits(p.multiply(q).bitLength()), ok(C3.basis(List.of(p, q), Telemetry.NONE)).bound());
    }

    @Test
    void smallFactorCheckReachesAPrimeBound() {
        BigInteger q = BigInteger.TWO.pow(1279).subtract(BigInteger.ONE);
        for (int j = 30; j < 400; j++) {
            BigInteger t = nextPrime(BigInteger.ONE.shiftLeft(j));
            long bound = Budget.forBits(q.multiply(t).bitLength() + 20);
            BigInteger at = BigInteger.valueOf(bound);
            if (!Arith.isProbablePrime(at) || q.multiply(t).multiply(at).bitLength() != q.multiply(t).bitLength() + 20) continue;
            BigInteger above = nextPrime(at.add(BigInteger.ONE));
            if (q.multiply(t).multiply(above).bitLength() != q.multiply(t).multiply(at).bitLength()) continue;
            assertEquals("refused: small factor", C3.basis(List.of(q.multiply(at), t), Telemetry.NONE).toString(), "j=" + j);
            assertEquals(bound, ok(C3.basis(List.of(q.multiply(above), t), Telemetry.NONE)).bound(), "j=" + j);
            return;
        }
        throw new AssertionError("no prime bound found");
    }

    private static BigInteger nextPrime(BigInteger x) {
        while (!Arith.isProbablePrime(x)) x = x.add(BigInteger.ONE);
        return x;
    }

    @Test
    void scanPastTheBoundIsRefused() {
        List<BigInteger> layers = List.of(BigInteger.valueOf(3001), BigInteger.valueOf(3011));
        C3.Basis full = ok(C3.basis(layers, Telemetry.NONE));
        int last = full.primes()[1];
        assertEquals("refused: budget exceeded", C3.scan(layers, last - 1, Telemetry.NONE).toString());
        assertEquals(last, ok(C3.scan(layers, last, Telemetry.NONE)).primes()[1]);
    }
}

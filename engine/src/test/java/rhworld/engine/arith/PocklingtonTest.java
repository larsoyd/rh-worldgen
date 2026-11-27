package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.contract.Verdict;

class PocklingtonTest {
    static BigInteger smoothPrime(int bits) {
        BigInteger three = BigInteger.valueOf(3);
        for (int i = 1; ; i++) {
            BigInteger p = BigInteger.ONE.shiftLeft(bits - 2 * i).multiply(three.pow(i)).add(BigInteger.ONE);
            if (Arith.isProbablePrime(p)) return p;
        }
    }

    static Map<String, Object> certificate(BigInteger p) {
        BigInteger m = p.subtract(BigInteger.ONE);
        int twos = m.getLowestSetBit();
        int threes = 0;
        for (BigInteger r = m.shiftRight(twos); r.mod(BigInteger.valueOf(3)).signum() == 0; r = r.divide(BigInteger.valueOf(3))) threes++;
        return Map.of("factors", List.of(List.of(BigInteger.TWO, BigInteger.valueOf(twos)), List.of(BigInteger.valueOf(3), BigInteger.valueOf(threes))),
                "witnesses", Map.of("2", witness(p, 2), "3", witness(p, 3)));
    }

    private static BigInteger witness(BigInteger p, int q) {
        BigInteger m = p.subtract(BigInteger.ONE);
        for (int a = 2; ; a++) {
            BigInteger x = BigInteger.valueOf(a);
            if (x.modPow(m, p).equals(BigInteger.ONE) && x.modPow(m.divide(BigInteger.valueOf(q)), p).subtract(BigInteger.ONE).gcd(p).equals(BigInteger.ONE)) {
                return x;
            }
        }
    }

    @Test
    void provesASmoothPrime() {
        BigInteger p = smoothPrime(600);
        assertEquals(new Verdict.Ok<>("proved"), Pocklington.check(p, certificate(p)));
    }

    @Test
    void provesWithANestedSubCertificate() {
        BigInteger q = smoothPrime(80);
        BigInteger p = null;
        for (int k = 2; p == null; k += 2) {
            BigInteger c = q.multiply(BigInteger.valueOf(k)).add(BigInteger.ONE);
            if (Arith.isProbablePrime(c) && k < 1000) p = c;
        }
        long k = p.subtract(BigInteger.ONE).divide(q).longValueExact();
        java.util.List<Object> factors = new java.util.ArrayList<>();
        Map<String, Object> w = new java.util.TreeMap<>();
        factors.add(List.of(q, BigInteger.ONE));
        w.put(q.toString(), witness(p, 0, q));
        for (long d = 2; k > 1; d++) {
            int e = 0;
            while (k % d == 0) {
                k /= d;
                e++;
            }
            if (e == 0) continue;
            factors.add(List.of(BigInteger.valueOf(d), BigInteger.valueOf(e)));
            w.put(Long.toString(d), witness(p, 0, BigInteger.valueOf(d)));
        }
        Map<String, Object> cert = Map.of("factors", factors, "witnesses", w, "sub", Map.of(q.toString(), certificate(q)));
        assertEquals(new Verdict.Ok<>("proved"), Pocklington.check(p, cert));
        assertEquals("refused: listed factor " + q + " not proved prime", Pocklington.check(p, Map.of("factors", factors, "witnesses", w)).toString());
    }

    static Map.Entry<BigInteger, Map<String, Object>> nested(BigInteger q) {
        BigInteger p = null;
        for (int k = 2; p == null; k += 2) {
            BigInteger c = q.multiply(BigInteger.valueOf(k)).add(BigInteger.ONE);
            if (Arith.isProbablePrime(c)) p = c;
        }
        long k = p.subtract(BigInteger.ONE).divide(q).longValueExact();
        java.util.List<Object> factors = new java.util.ArrayList<>();
        Map<String, Object> w = new java.util.TreeMap<>();
        factors.add(List.of(q, BigInteger.ONE));
        w.put(q.toString(), witness(p, 0, q));
        for (long d = 2; k > 1; d++) {
            int e = 0;
            while (k % d == 0) {
                k /= d;
                e++;
            }
            if (e == 0) continue;
            factors.add(List.of(BigInteger.valueOf(d), BigInteger.valueOf(e)));
            w.put(Long.toString(d), witness(p, 0, BigInteger.valueOf(d)));
        }
        return Map.entry(p, Map.of("factors", factors, "witnesses", w, "sub", Map.of(q.toString(), certificate(q))));
    }

    private static BigInteger witness(BigInteger p, int unused, BigInteger q) {
        BigInteger m = p.subtract(BigInteger.ONE);
        for (int a = 2; ; a++) {
            BigInteger x = BigInteger.valueOf(a);
            if (x.modPow(m, p).equals(BigInteger.ONE) && x.modPow(m.divide(q), p).subtract(BigInteger.ONE).gcd(p).equals(BigInteger.ONE)) return x;
        }
    }

    @Test
    void acceptsIntegersWrittenAsDecimalStrings() {
        BigInteger p = smoothPrime(600);
        Map<String, Object> good = certificate(p);
        @SuppressWarnings("unchecked")
        List<List<BigInteger>> f = (List<List<BigInteger>>) good.get("factors");
        @SuppressWarnings("unchecked")
        Map<String, BigInteger> w = (Map<String, BigInteger>) good.get("witnesses");
        Map<String, Object> strings = Map.of("factors", f.stream().map(x -> List.of(x.get(0).toString(), x.get(1).toString())).toList(),
                "witnesses", Map.of("2", w.get("2").toString(), "3", w.get("3").toString()));
        assertEquals(new Verdict.Ok<>("proved"), Pocklington.check(p, strings));
        assertEquals("refused: certificate malformed", Pocklington.check(p, Map.of("factors", List.of(List.of("2x", "1")), "witnesses", Map.of())).toString());
    }

    @Test
    void refusesBrokenCertificates() {
        BigInteger p = smoothPrime(600);
        Map<String, Object> good = certificate(p);
        @SuppressWarnings("unchecked")
        List<List<BigInteger>> f = (List<List<BigInteger>>) good.get("factors");
        assertEquals("refused: factored part too small",
                Pocklington.check(p, Map.of("factors", List.of(f.get(1)), "witnesses", good.get("witnesses"))).toString());
        assertEquals("refused: factored part does not divide n-1",
                Pocklington.check(p, Map.of("factors", List.of(List.of(BigInteger.valueOf(5), BigInteger.ONE), f.get(0), f.get(1)),
                        "witnesses", Map.of("2", BigInteger.TWO, "3", BigInteger.TWO, "5", BigInteger.TWO))).toString());
        assertEquals("refused: witness fails for factor 2",
                Pocklington.check(p, Map.of("factors", f, "witnesses", Map.of("2", BigInteger.ONE, "3", BigInteger.TWO))).toString());
        BigInteger composite = p.multiply(BigInteger.valueOf(7)).multiply(BigInteger.valueOf(13));
        assertEquals("refused: certificate malformed", Pocklington.check(p, Map.of("factors", "x")).toString());
        assertEquals("refused: factored part does not divide n-1", Pocklington.check(p, Map.of("factors", List.of(List.of(BigInteger.TWO,
                BigInteger.valueOf(1_000_000_000))), "witnesses", Map.of("2", BigInteger.TWO))).toString());
        assertEquals(true, Pocklington.check(composite, good) instanceof Verdict.Refused);
    }

    @Test
    void hugeExponentsAreRefusedWithoutComputingThePower() {
        BigInteger p = smoothPrime(600);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> assertEquals(
                "refused: factored part does not divide n-1", Pocklington.check(p, Map.of("factors", List.of(List.of(BigInteger.valueOf(3),
                        BigInteger.valueOf(1_000_000_000))), "witnesses", Map.of("3", BigInteger.TWO))).toString()));
    }

    @Test
    void smallListedFactorsProvedByTrialDivisionOnly() {
        assertEquals(true, Pocklington.provedSmall(BigInteger.valueOf(4_294_967_291L)));
        assertEquals(false, Pocklington.provedSmall(BigInteger.valueOf(4_294_967_297L)));
        assertEquals(false, Pocklington.provedSmall(BigInteger.ONE.shiftLeft(32).add(BigInteger.valueOf(15))));
        assertEquals(false, Pocklington.provedSmall(BigInteger.ONE));
        assertEquals(false, Pocklington.provedSmall(BigInteger.valueOf(4_294_967_295L)));
        assertEquals(false, Pocklington.provedSmall(BigInteger.valueOf(561)));
    }
}

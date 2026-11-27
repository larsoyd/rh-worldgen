package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import rhworld.engine.Golden;
import java.util.List;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ArithTest {
    private static int euler(long a, long p) {
        BigInteger r = BigInteger.valueOf(Math.floorMod(a, p)).modPow(BigInteger.valueOf((p - 1) / 2), BigInteger.valueOf(p));
        return r.equals(BigInteger.valueOf(p - 1)) ? -1 : r.intValue();
    }

    private static boolean naivePrime(long n) {
        if (n < 2) return false;
        for (long d = 2; d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    @Test
    void jacobiMatchesEulerOnSmallPrimes() {
        for (long p = 3; p < 400; p += 2) {
            if (!naivePrime(p)) continue;
            for (long a = -p; a < 2 * p; a++) {
                assertEquals(euler(a, p), Arith.jacobi(BigInteger.valueOf(a), BigInteger.valueOf(p)), "a=" + a + " p=" + p);
                assertEquals(euler(a, p), Arith.jacobi(a, BigInteger.valueOf(p)), "small a=" + a + " p=" + p);
            }
        }
    }

    @Test
    void jacobiIsProductOverFactors() {
        for (long n = 3; n < 2000; n += 2) {
            for (long a = 0; a < 60; a++) {
                int want = 1;
                long m = n;
                for (long d = 3; m > 1; d += 2) {
                    while (m % d == 0) {
                        want *= euler(a, d);
                        m /= d;
                    }
                }
                assertEquals(want, Arith.jacobi(a, BigInteger.valueOf(n)), "a=" + a + " n=" + n);
            }
        }
    }

    @Test
    void smallArgumentPathAgreesWithGeneralPath() {
        SplittableRandom rng = new SplittableRandom(11);
        for (int i = 0; i < 3000; i++) {
            BigInteger n = new BigInteger(64 + rng.nextInt(2000), new java.util.Random(rng.nextLong())).setBit(0);
            long a = i < 1000 ? i : rng.nextLong(Long.MAX_VALUE);
            assertEquals(Arith.jacobi(BigInteger.valueOf(a), n), Arith.jacobi(a, n), "a=" + a + " n=" + n);
        }
    }

    @Test
    void jacobiRefusesEvenOrNonPositiveModulus() {
        for (long n : new long[] {10, 0, -3}) {
            var e = assertThrows(IllegalArgumentException.class, () -> Arith.jacobi(3, BigInteger.valueOf(n)));
            assertTrue(e.getMessage().contains("odd positive"), e.getMessage());
        }
    }

    @Test
    void jacobiMatchesOracle() {
        List<List<Object>> cases = Golden.list("jacobi");
        assertTrue(cases.size() > 500, "cases=" + cases.size());
        for (List<Object> c : cases) {
            BigInteger a = new BigInteger((String) c.get(0));
            BigInteger n = new BigInteger((String) c.get(1));
            assertEquals(((BigInteger) c.get(2)).intValue(), Arith.jacobi(a, n), "a=" + a + " n=" + n);
        }
    }

    @Test
    void probablePrimeExactOnSmallRange() {
        for (long n = -5; n < 20000; n++) {
            assertEquals(naivePrime(n), Arith.isProbablePrime(BigInteger.valueOf(n)), "n=" + n);
        }
    }

    @Test
    void probablePrimeMatchesOracle() {
        List<List<Object>> cases = Golden.list("prime");
        assertTrue(cases.size() > 3000, "cases=" + cases.size());
        for (List<Object> c : cases) {
            BigInteger n = new BigInteger((String) c.get(0));
            assertEquals(c.get(1), Arith.isProbablePrime(n), "n=" + n);
        }
    }

    @Test
    void probablePrimeRejectsStrongPseudoprimes() {
        for (String s : new String[] {"2047", "3215031751", "3825123056546413051", "318665857834031151167461"}) {
            assertFalse(Arith.isProbablePrime(new BigInteger(s)), s);
        }
        assertTrue(Arith.isProbablePrime(BigInteger.TWO.pow(521).subtract(BigInteger.ONE)));
    }

    @Test
    void perfectSquareDetection() {
        SplittableRandom rng = new SplittableRandom(3);
        for (int i = 0; i < 500; i++) {
            BigInteger r = new BigInteger(1 + rng.nextInt(1100), new java.util.Random(rng.nextLong()));
            BigInteger sq = r.multiply(r);
            assertTrue(Arith.isPerfectSquare(sq), "r=" + r);
            if (r.signum() > 0) {
                assertFalse(Arith.isPerfectSquare(sq.add(BigInteger.ONE)), "r^2+1 r=" + r);
                assertFalse(Arith.isPerfectSquare(sq.subtract(BigInteger.ONE)) && r.compareTo(BigInteger.ONE) > 0, "r^2-1 r=" + r);
            }
        }
        assertFalse(Arith.isPerfectSquare(BigInteger.valueOf(-4)));
    }

    @Test
    void leastFactorFindsSmallestPrimeUpToLimit() {
        BigInteger p = BigInteger.TWO.pow(127).subtract(BigInteger.ONE);
        assertEquals(OptionalLong.of(504103), Arith.leastFactor(p.multiply(BigInteger.valueOf(504103L * 504121L)), 504103));
        assertEquals(OptionalLong.empty(), Arith.leastFactor(p.multiply(BigInteger.valueOf(504103)), 504102));
        assertEquals(OptionalLong.of(13), Arith.leastFactor(p.multiply(BigInteger.valueOf(504101)), 504101));
        assertEquals(OptionalLong.of(3), Arith.leastFactor(BigInteger.valueOf(9), 3));
        assertEquals(OptionalLong.of(2), Arith.leastFactor(BigInteger.valueOf(1L << 40), 2));
        assertEquals(OptionalLong.empty(), Arith.leastFactor(p, 2_015_165));
        for (long n = 2; n < 3000; n++) {
            long want = 0;
            for (long d = 2; d <= 50 && want == 0; d++) if (n % d == 0) want = d;
            OptionalLong got = Arith.leastFactor(BigInteger.valueOf(n), 50);
            assertEquals(want == 0 ? OptionalLong.empty() : OptionalLong.of(want), got, "n=" + n);
        }
    }
}

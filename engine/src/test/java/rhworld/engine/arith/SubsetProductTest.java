package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import rhworld.engine.contract.Verdict;

class SubsetProductTest {
    private static BigInteger naive(long[] bits, int[] primes, BigInteger n) {
        BigInteger x = BigInteger.ONE;
        for (int i = 0; i < primes.length; i++) {
            if ((bits[i >>> 6] >>> (i & 63) & 1) == 1) x = x.multiply(BigInteger.valueOf(primes[i])).mod(n);
        }
        return x;
    }

    @Test
    void matchesNaiveProductOnRandomSubsets() {
        SplittableRandom rng = new SplittableRandom(41);
        int[] primes = Primes.upTo(Budget.forBits(1024));
        for (int t = 0; t < 30; t++) {
            BigInteger n = new BigInteger(64 + rng.nextInt(1100), new Random(rng.nextLong())).setBit(0).setBit(63);
            int len = 1 + rng.nextInt(primes.length);
            int[] ps = java.util.Arrays.copyOf(primes, len);
            long[] bits = new long[(len + 63) / 64];
            for (int i = 0; i < len; i++) if (rng.nextInt(t % 3 == 0 ? 50 : 2) == 0) bits[i >>> 6] |= 1L << (i & 63);
            assertEquals(new Verdict.Ok<>(naive(bits, ps, n)), Arith.subsetProduct(bits, ps, n), "t=" + t + " len=" + len);
        }
    }

    @Test
    void largePrimesAndFullSetStayExact() {
        int[] ps = {2147483647, 2147483629, 2147483587, 2147483579, 2147483563};
        long[] all = {0b11111};
        BigInteger n = BigInteger.TWO.pow(127).subtract(BigInteger.ONE);
        assertEquals(new Verdict.Ok<>(naive(all, ps, n)), Arith.subsetProduct(all, ps, n));
        assertEquals(new Verdict.Ok<>(BigInteger.ONE), Arith.subsetProduct(new long[1], ps, n));
    }

    @Test
    void refusesMismatchedOrStrayBits() {
        int[] ps = Primes.upTo(1000);
        BigInteger n = BigInteger.valueOf(1_000_003);
        assertEquals(new Verdict.Refused<>("bits do not match primes words=1 primes=" + ps.length), Arith.subsetProduct(new long[1], ps, n));
        long[] stray = new long[(ps.length + 63) / 64];
        stray[stray.length - 1] = Long.MIN_VALUE;
        assertEquals(new Verdict.Refused<>("bit set beyond primes index=" + (64 * stray.length - 1)), Arith.subsetProduct(stray, ps, n));
        assertEquals(new Verdict.Refused<>("modulus must exceed one n=1"), Arith.subsetProduct(new long[3], ps, BigInteger.ONE));
    }
}

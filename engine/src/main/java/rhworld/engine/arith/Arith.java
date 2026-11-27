package rhworld.engine.arith;

import java.math.BigInteger;
import java.util.OptionalLong;

public final class Arith {
    static final int[] BASES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};

    private Arith() {}

    public static int jacobi(BigInteger a, BigInteger n) {
        requireOddPositive(n);
        a = a.mod(n);
        int sign = 1;
        while (a.signum() != 0) {
            if (n.bitLength() < 63) return sign * jacobi(a.longValueExact(), n.longValueExact());
            int twos = a.getLowestSetBit();
            a = a.shiftRight(twos);
            int n8 = n.intValue() & 7;
            if ((twos & 1) == 1 && (n8 == 3 || n8 == 5)) sign = -sign;
            if ((a.intValue() & 3) == 3 && (n8 & 3) == 3) sign = -sign;
            BigInteger r = n.mod(a);
            n = a;
            a = r;
        }
        return n.equals(BigInteger.ONE) ? sign : 0;
    }

    public static int jacobi(long a, BigInteger n) {
        requireOddPositive(n);
        if (a < 0) return jacobi(BigInteger.valueOf(a), n);
        if (n.bitLength() < 63) return jacobi(a, n.longValueExact());
        if (a == 0) return 0;
        int sign = 1;
        int twos = Long.numberOfTrailingZeros(a);
        a >>>= twos;
        int n8 = n.intValue() & 7;
        if ((twos & 1) == 1 && (n8 == 3 || n8 == 5)) sign = -sign;
        if (a == 1) return sign;
        if ((a & 3) == 3 && (n8 & 3) == 3) sign = -sign;
        return sign * jacobi(n.mod(BigInteger.valueOf(a)).longValueExact(), a);
    }

    static int jacobi(long a, long n) {
        a %= n;
        int sign = 1;
        while (a != 0) {
            int twos = Long.numberOfTrailingZeros(a);
            a >>>= twos;
            long n8 = n & 7;
            if ((twos & 1) == 1 && (n8 == 3 || n8 == 5)) sign = -sign;
            if ((a & 3) == 3 && (n & 3) == 3) sign = -sign;
            long r = n % a;
            n = a;
            a = r;
        }
        return n == 1 ? sign : 0;
    }

    // a prime always passes so a fooled base can only turn a composite into a refusal
    public static boolean isProbablePrime(BigInteger n) {
        if (n.compareTo(BigInteger.TWO) < 0) return false;
        for (int p : BASES) {
            if (n.mod(BigInteger.valueOf(p)).signum() == 0) return n.equals(BigInteger.valueOf(p));
        }
        BigInteger m = n.subtract(BigInteger.ONE);
        int s = m.getLowestSetBit();
        BigInteger d = m.shiftRight(s);
        for (int base : BASES) {
            BigInteger x = BigInteger.valueOf(base).modPow(d, n);
            if (x.equals(BigInteger.ONE) || x.equals(m)) continue;
            boolean witness = true;
            for (int i = 1; i < s && witness; i++) {
                x = x.multiply(x).mod(n);
                if (x.equals(m)) witness = false;
            }
            if (witness) return false;
        }
        return true;
    }

    public static boolean isPerfectSquare(BigInteger n) {
        if (n.signum() < 0) return false;
        BigInteger r = n.sqrt();
        return r.multiply(r).equals(n);
    }

    public static OptionalLong leastFactor(BigInteger n, long limit) {
        if (n.signum() <= 0) throw new IllegalArgumentException("leastFactor needs a positive n=" + n);
        int[] limbs = limbs(n);
        for (int p : Primes.upTo(limit)) {
            if (BigInteger.valueOf(p).compareTo(n) > 0) break;
            if (remainder(limbs, p) == 0) return OptionalLong.of(p);
        }
        return OptionalLong.empty();
    }

    static int[] limbs(BigInteger n) {
        byte[] b = n.toByteArray();
        int[] out = new int[(b.length + 3) / 4];
        for (int i = 0; i < b.length; i++) {
            int fromEnd = b.length - 1 - i;
            out[out.length - 1 - fromEnd / 4] |= (b[i] & 0xff) << (8 * (fromEnd % 4));
        }
        return out;
    }

    static long remainder(int[] limbs, int p) {
        long r = 0;
        for (int limb : limbs) r = ((r << 32) | (limb & 0xffffffffL)) % p;
        return r;
    }

    private static void requireOddPositive(BigInteger n) {
        if (n.signum() <= 0 || !n.testBit(0)) throw new IllegalArgumentException("jacobi needs an odd positive modulus n=" + n);
    }
}

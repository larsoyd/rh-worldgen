package rhworld.mechanics;

import java.math.BigInteger;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.arith.Primes;

public final class WildGate {
    private final BigInteger n;
    private final int[] primes;
    private final long[] cold;
    private final int witness;

    WildGate(BigInteger n, int[] primes) {
        OddModulus m = new OddModulus(n);
        this.n = n;
        this.primes = primes.clone();
        this.cold = new long[words(primes.length)];
        int w = -1;
        for (int i = 0; i < primes.length; i++) {
            if (m.jacobi(primes[i]) == -1) {
                cold[i >>> 6] |= 1L << (i & 63);
                if (w < 0) w = i;
            }
        }
        this.witness = w;
    }

    public static WildGate forModulus(BigInteger n) {
        return new WildGate(n, Primes.upTo(Budget.forBits(n.bitLength())));
    }

    public int size() {
        return primes.length;
    }

    public int[] primes() {
        return primes.clone();
    }

    public long witness() {
        return primes[requireWitness()];
    }

    public BigInteger ride(BigInteger x, Colour colour, long[] e) {
        int w = requireWitness();
        if (e.length != words(primes.length))
            throw new IllegalArgumentException("refused: dice length words=" + e.length + " expected=" + words(primes.length));
        int tail = primes.length & 63;
        if (tail != 0 && e[e.length - 1] >>> tail != 0)
            throw new IllegalArgumentException("refused: dice bits past the gate size size=" + primes.length);
        if (x.signum() <= 0 || x.compareTo(n) >= 0 || Arith.jacobi(x, n) == 0)
            throw new IllegalArgumentException("refused: attunement not a unit below n");
        long[] bits = e.clone();
        if (Colour.ofJacobi(Arith.jacobi(x, n)) == colour == (parity(bits) == 1)) bits[w >>> 6] ^= 1L << (w & 63);
        return x.multiply(product(bits)).mod(n);
    }

    private int parity(long[] bits) {
        int c = 0;
        for (int i = 0; i < bits.length; i++) c += Long.bitCount(bits[i] & cold[i]);
        return c & 1;
    }

    private BigInteger product(long[] bits) {
        BigInteger acc = BigInteger.ONE;
        long batch = 1;
        for (int i = 0; i < primes.length; i++) {
            if ((bits[i >>> 6] >>> (i & 63) & 1) == 0) continue;
            if (batch > Long.MAX_VALUE / primes[i]) {
                acc = acc.multiply(BigInteger.valueOf(batch)).mod(n);
                batch = 1;
            }
            batch *= primes[i];
        }
        return acc.multiply(BigInteger.valueOf(batch)).mod(n);
    }

    private int requireWitness() {
        if (witness < 0) throw new IllegalStateException("refused: budget exceeded no jacobi -1 prime in the gate size=" + primes.length);
        return witness;
    }

    private static int words(int bits) {
        return (bits + 63) / 64;
    }
}

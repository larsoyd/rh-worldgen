package rhworld.mechanics;

import java.math.BigInteger;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Budget;
import rhworld.engine.arith.OddModulus;
import rhworld.engine.arith.Primes;
import rhworld.engine.contract.Verdict;

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
        if (x.signum() <= 0 || x.compareTo(n) >= 0 || Arith.jacobi(x, n) == 0)
            throw new IllegalArgumentException("refused: attunement not a unit below n");
        BigInteger y = switch (Arith.subsetProduct(e, primes, n)) {
            case Verdict.Ok<BigInteger> ok -> ok.value();
            case Verdict.Refused<BigInteger> r -> throw new IllegalArgumentException(r.toString());
        };
        if (Colour.ofJacobi(Arith.jacobi(x, n)) == colour == (parity(e) == 1)) {
            BigInteger step = BigInteger.valueOf(primes[w]);
            y = y.multiply((e[w >>> 6] >>> (w & 63) & 1) == 1 ? step.modInverse(n) : step).mod(n);
        }
        return x.multiply(y).mod(n);
    }

    private int parity(long[] bits) {
        int c = 0;
        for (int i = 0; i < bits.length; i++) c += Long.bitCount(bits[i] & cold[i]);
        return c & 1;
    }

    private int requireWitness() {
        if (witness < 0) throw new IllegalStateException("refused: budget exceeded no jacobi -1 prime in the gate size=" + primes.length);
        return witness;
    }

    private static int words(int bits) {
        return (bits + 63) / 64;
    }
}

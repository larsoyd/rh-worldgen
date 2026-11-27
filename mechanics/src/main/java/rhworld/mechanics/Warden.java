package rhworld.mechanics;

import java.math.BigInteger;
import rhworld.engine.arith.Arith;

public final class Warden {
    private final BigInteger p;
    private final BigInteger q;
    private final BigInteger n;

    public Warden(BigInteger p, BigInteger q) {
        if (p.equals(q)) throw new IllegalArgumentException("refused: key factors are equal");
        if (!p.testBit(0) || !q.testBit(0) || !Arith.isProbablePrime(p) || !Arith.isProbablePrime(q))
            throw new IllegalArgumentException("refused: key factor not an odd prime");
        this.p = p;
        this.q = q;
        this.n = p.multiply(q);
    }

    public BigInteger modulus() {
        return n;
    }

    // class bits only, never a root or a factor
    Realm realm(BigInteger x) {
        return Realm.of(Arith.jacobi(x, p), Arith.jacobi(x, q));
    }

    int rank(int[] primes) {
        boolean[] span = new boolean[4];
        span[0] = true;
        for (int l : primes) {
            int v = realm(BigInteger.valueOf(l)).ordinal();
            if (span[v]) continue;
            for (int a = 0; a < 4; a++) if (span[a]) span[Realm.values()[a].plus(Realm.values()[v]).ordinal()] = true;
            if (span[0] && span[1] && span[2] && span[3]) return 2;
        }
        int size = 0;
        for (boolean s : span) if (s) size++;
        return Integer.numberOfTrailingZeros(size);
    }
}

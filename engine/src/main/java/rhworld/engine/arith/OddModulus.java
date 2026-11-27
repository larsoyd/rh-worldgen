package rhworld.engine.arith;

import java.math.BigInteger;
import java.util.OptionalLong;

public final class OddModulus {
    private final BigInteger n;
    private final int[] limbs;
    private final int n8;

    public OddModulus(BigInteger n) {
        if (n.signum() <= 0 || !n.testBit(0)) throw new IllegalArgumentException("odd positive modulus needed n=" + n);
        this.n = n;
        this.limbs = Arith.limbs(n);
        this.n8 = n.intValue() & 7;
    }

    public BigInteger value() {
        return n;
    }

    public int jacobi(long a) {
        if (a < 0 || a > Integer.MAX_VALUE || n.bitLength() < 63) return Arith.jacobi(a, n);
        if (a == 0) return n.equals(BigInteger.ONE) ? 1 : 0;
        int sign = 1;
        int twos = Long.numberOfTrailingZeros(a);
        a >>>= twos;
        if ((twos & 1) == 1 && (n8 == 3 || n8 == 5)) sign = -sign;
        if (a == 1) return sign;
        if ((a & 3) == 3 && (n8 & 3) == 3) sign = -sign;
        return sign * Arith.jacobi(Arith.remainder(limbs, (int) a), a);
    }

    public OptionalLong leastNonResidue(long cap) {
        for (long a = 1; a <= cap; a++) {
            if (jacobi(a) == -1) return OptionalLong.of(a);
        }
        return OptionalLong.empty();
    }
}

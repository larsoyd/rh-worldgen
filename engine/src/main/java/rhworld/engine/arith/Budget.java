package rhworld.engine.arith;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public final class Budget {
    public static final int MAX_BITS = 1 << 20;

    // ln 2 truncated to 120 digits, so the true value lies in [LN2_LOW, LN2_LOW + ULP)
    private static final BigDecimal LN2_LOW = new BigDecimal(
            "0.693147180559945309417232121458176568075500134360255254120680009493393621969694715605863326996418687542001481020570685733");
    private static final BigDecimal ULP = BigDecimal.ONE.movePointLeft(120);
    private static final BigDecimal LN2_HIGH = LN2_LOW.add(ULP);

    private Budget() {}

    public static long forBits(int k) {
        if (k < 2 || k > MAX_BITS) throw new IllegalArgumentException("bit length out of range k=" + k);
        BigDecimal kk = BigDecimal.valueOf((long) k * k);
        BigInteger low = kk.multiply(LN2_LOW.pow(2)).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        BigInteger high = kk.multiply(LN2_HIGH.pow(2)).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        if (!low.equals(high)) throw new IllegalStateException("budget not resolved k=" + k + " low=" + low + " high=" + high);
        return low.longValueExact();
    }

    public static long side(int k) {
        return BigInteger.valueOf(forBits(k) - 1).sqrt().longValueExact() + 1;
    }
}

package rhworld.engine.arith;

import java.math.BigInteger;
import java.util.Map;

public final class PocklingtonAccess {
    private PocklingtonAccess() {}

    public static BigInteger smoothPrime(int bits) {
        return PocklingtonTest.smoothPrime(bits);
    }

    public static Map<String, Object> certificate(BigInteger p) {
        return PocklingtonTest.certificate(p);
    }
}

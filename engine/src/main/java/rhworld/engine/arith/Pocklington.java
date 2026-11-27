package rhworld.engine.arith;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import rhworld.engine.contract.Verdict;

public final class Pocklington {
    private static final BigInteger SMALL_LIMIT = BigInteger.ONE.shiftLeft(32);

    private Pocklington() {}

    public static Verdict<String> check(BigInteger n, Map<String, Object> cert) {
        try {
            return checked(n, cert);
        } catch (ClassCastException | NullPointerException | ArithmeticException | NumberFormatException e) {
            return new Verdict.Refused<>("certificate malformed");
        }
    }

    @SuppressWarnings("unchecked")
    private static Verdict<String> checked(BigInteger n, Map<String, Object> cert) {
        List<List<Object>> factors = (List<List<Object>>) cert.get("factors");
        Map<String, Object> subs = (Map<String, Object>) cert.getOrDefault("sub", Map.of());
        Map<String, Object> witnesses = (Map<String, Object>) cert.get("witnesses");
        BigInteger m = n.subtract(BigInteger.ONE), f = BigInteger.ONE;
        boolean oversized = false;
        for (List<Object> qe : factors) {
            BigInteger q = integer(qe.get(0));
            int e = integer(qe.get(1)).intValueExact();
            if (e < 1) return new Verdict.Refused<>("certificate malformed");
            Map<String, Object> sub = (Map<String, Object>) subs.get(q.toString());
            boolean proved = provedSmall(q) || (sub != null && q.compareTo(n) < 0 && check(q, sub) instanceof Verdict.Ok<String>);
            if (!proved) return new Verdict.Refused<>("listed factor " + q + " not proved prime");
            oversized |= e > n.bitLength();
            if (!oversized) f = f.multiply(q.pow(e));
        }
        if (oversized || m.mod(f).signum() != 0) return new Verdict.Refused<>("factored part does not divide n-1");
        if (f.multiply(f).compareTo(n) <= 0) return new Verdict.Refused<>("factored part too small");
        for (List<Object> qe : factors) {
            BigInteger q = integer(qe.get(0)), a = integer(witnesses.get(q.toString()));
            if (!a.modPow(m, n).equals(BigInteger.ONE) || !a.modPow(m.divide(q), n).subtract(BigInteger.ONE).gcd(n).equals(BigInteger.ONE)) {
                return new Verdict.Refused<>("witness fails for factor " + q);
            }
        }
        return new Verdict.Ok<>("proved");
    }

    private static BigInteger integer(Object o) {
        if (o instanceof BigInteger b) return b;
        if (o instanceof String s && s.matches("[0-9]+")) return new BigInteger(s);
        throw new ClassCastException("not an integer " + o);
    }

    // the fixed 25 bases are exact far beyond 2^32 so below it the test is a proof
    static boolean provedSmall(BigInteger q) {
        return q.compareTo(BigInteger.ONE) > 0 && q.compareTo(SMALL_LIMIT) < 0 && Arith.isProbablePrime(q);
    }
}

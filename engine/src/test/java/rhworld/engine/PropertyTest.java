package rhworld.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.PrimeTable;
import rhworld.engine.contract.Verdict;
import rhworld.engine.json.Json;

class PropertyTest {
    static BigInteger big(SplittableRandom r, int maxBits) {
        return new BigInteger(1 + r.nextInt(maxBits), new Random(r.nextLong()));
    }

    static BigInteger odd(SplittableRandom r, int maxBits) {
        return big(r, maxBits).setBit(0);
    }

    static BigInteger signed(SplittableRandom r, int maxBits) {
        BigInteger x = r.nextInt(4) == 0 ? BigInteger.valueOf(r.nextInt(200)) : big(r, maxBits);
        return r.nextBoolean() ? x : x.negate();
    }

    record Triple(BigInteger a, BigInteger b, BigInteger n) {}

    @Test
    void jacobiIsMultiplicativeInTheTop() {
        Props.forAll("jacobi-top", 3000, r -> new Triple(signed(r, 2200), signed(r, 2200), odd(r, 2100)), t ->
                assertEquals(Arith.jacobi(t.a(), t.n()) * Arith.jacobi(t.b(), t.n()), Arith.jacobi(t.a().multiply(t.b()), t.n())));
    }

    @Test
    void jacobiIsMultiplicativeInTheBottom() {
        Props.forAll("jacobi-bottom", 2000, r -> new Triple(signed(r, 2200), odd(r, 1100), odd(r, 1100)), t ->
                assertEquals(Arith.jacobi(t.a(), t.b()) * Arith.jacobi(t.a(), t.n()), Arith.jacobi(t.a(), t.b().multiply(t.n()))));
    }

    @Test
    void quadraticReciprocityForOddCoprimePairs() {
        Props.forAll("reciprocity", 2000, r -> new Triple(BigInteger.ZERO, odd(r, 1500), odd(r, 1500)), t -> {
            BigInteger m = t.b(), n = t.n();
            int sign = m.testBit(1) && n.testBit(1) ? -1 : 1;
            int product = Arith.jacobi(m, n) * Arith.jacobi(n, m);
            assertEquals(m.gcd(n).equals(BigInteger.ONE) ? sign : 0, product);
        });
    }

    @Test
    void smallArgumentJacobiAgreesWithTheGeneralOne() {
        Props.forAll("jacobi-long", 3000, r -> new Triple(BigInteger.valueOf(r.nextLong()), BigInteger.ZERO, odd(r, 2100)), t ->
                assertEquals(Arith.jacobi(t.a(), t.n()), Arith.jacobi(t.a().longValueExact(), t.n())));
    }

    record Subsets(PrimeTable table, long[] e1, long[] e2, BigInteger n) {
        @Override
        public String toString() {
            return "Subsets[primes=" + table.size() + ", n=" + n + "]";
        }
    }

    static long[] bits(SplittableRandom r, int count) {
        long[] w = new long[(count + 63) / 64];
        int density = 1 + r.nextInt(8);
        for (int i = 0; i < count; i++) if (r.nextInt(density) == 0) w[i >>> 6] |= 1L << (i & 63);
        return w;
    }

    static BigInteger product(long[] e, PrimeTable t, BigInteger n) {
        if (Arith.subsetProduct(e, t, n) instanceof Verdict.Ok<BigInteger> ok) return ok.value();
        throw new AssertionError("refused");
    }

    @Test
    void subsetProductEqualsTheNaiveProductAndObeysTheXorLaw() {
        Props.forAll("subset-product", 300, r -> {
            PrimeTable t = PrimeTable.upTo(2 + r.nextInt(r.nextBoolean() ? 3000 : 600_000));
            return new Subsets(t, bits(r, t.size()), bits(r, t.size()), odd(r, 2100).add(BigInteger.TWO));
        }, s -> {
            BigInteger naive = BigInteger.ONE;
            for (int i = 0; i < s.table().size(); i++) {
                if ((s.e1()[i >>> 6] >>> (i & 63) & 1) == 1) naive = naive.multiply(BigInteger.valueOf(s.table().get(i))).mod(s.n());
            }
            assertEquals(naive, product(s.e1(), s.table(), s.n()));
            long[] xor = new long[s.e1().length], and = new long[s.e1().length];
            for (int i = 0; i < xor.length; i++) {
                xor[i] = s.e1()[i] ^ s.e2()[i];
                and[i] = s.e1()[i] & s.e2()[i];
            }
            BigInteger both = product(and, s.table(), s.n());
            assertEquals(product(s.e1(), s.table(), s.n()).multiply(product(s.e2(), s.table(), s.n())).mod(s.n()),
                    product(xor, s.table(), s.n()).multiply(both).multiply(both).mod(s.n()));
        });
    }

    static Object tree(SplittableRandom r, int depth) {
        int kind = depth > 3 ? r.nextInt(4) : r.nextInt(6);
        return switch (kind) {
            case 0 -> signed(r, 400);
            case 1 -> text(r);
            case 2 -> r.nextBoolean();
            case 3 -> null;
            case 4 -> {
                List<Object> l = new ArrayList<>();
                for (int i = r.nextInt(5); i > 0; i--) l.add(tree(r, depth + 1));
                yield l;
            }
            default -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = r.nextInt(5); i > 0; i--) m.put(text(r), tree(r, depth + 1));
                yield m;
            }
        };
    }

    static String text(SplittableRandom r) {
        StringBuilder sb = new StringBuilder();
        String alphabet = "ab/\\\"\n\t\b\f\r\u0001\u001fé中 z0";
        for (int i = r.nextInt(8); i > 0; i--) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    @Test
    void jsonWriteParseIsTheIdentityOnCanonicalText() {
        int n = Props.forAll("json-round-trip", 3000, r -> tree(r, 0), v -> {
            String text = Json.write(v);
            assertEquals(text, Json.write(Json.parse(text)));
            Object back = Json.parse(text);
            assertEquals(text, Json.write(back));
            assertTrue(!text.contains("\n") && !text.contains(": "), text);
        });
        assertEquals(3000, n);
    }
}

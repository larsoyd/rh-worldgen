package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import rhworld.engine.contract.Verdict;

class PrimeTableTest {
    @Test
    void noPublicArithmeticTakesARawPrimeArray() throws Exception {
        Class<?> table = Class.forName("rhworld.engine.arith.PrimeTable");
        List<String> raw = Arrays.stream(Arith.class.getMethods())
                .filter(m -> m.getName().equals("subsetProduct") && Arrays.asList(m.getParameterTypes()).contains(int[].class))
                .map(Object::toString).toList();
        assertEquals(List.of(), raw);
        assertEquals(1, Arrays.stream(Arith.class.getMethods())
                .filter(m -> m.getName().equals("subsetProduct") && Arrays.asList(m.getParameterTypes()).contains(table)).count());
        assertTrue(Arrays.stream(table.getDeclaredConstructors()).noneMatch(c -> Modifier.isPublic(c.getModifiers())));
    }

    @Test
    void tableHoldsExactlyThePrimesUpToTheLimit() {
        PrimeTable t = PrimeTable.upTo(1000);
        assertEquals(168, t.size());
        assertEquals(2, t.get(0));
        assertEquals(997, t.get(167));
        assertEquals(1000, t.limit());
        int[] copy = t.toArray();
        copy[0] = 4;
        assertEquals(2, t.get(0));
        assertEquals(Primes.upTo(503_791).length, PrimeTable.upTo(503_791).size());
        assertThrows(IndexOutOfBoundsException.class, () -> t.get(168));
        assertThrows(IllegalArgumentException.class, () -> PrimeTable.upTo(1));
    }

    @Test
    void subsetProductOverATable() {
        PrimeTable t = PrimeTable.upTo(100);
        BigInteger n = BigInteger.valueOf(1_000_003);
        long[] all = {(1L << t.size()) - 1};
        BigInteger want = BigInteger.ONE;
        for (int i = 0; i < t.size(); i++) want = want.multiply(BigInteger.valueOf(t.get(i))).mod(n);
        assertEquals(new Verdict.Ok<>(want), Arith.subsetProduct(all, t, n));
        assertEquals(new Verdict.Refused<>("bits do not match primes words=2 primes=25"), Arith.subsetProduct(new long[2], t, n));
    }
}

package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.Random;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class OddModulusTest {
    @Test
    void agreesWithGeneralJacobi() {
        SplittableRandom rng = new SplittableRandom(29);
        for (int i = 0; i < 400; i++) {
            BigInteger n = new BigInteger(2 + rng.nextInt(2100), new Random(rng.nextLong())).setBit(0);
            OddModulus m = new OddModulus(n);
            for (int j = 0; j < 40; j++) {
                long a = switch (j % 4) {
                    case 0 -> j;
                    case 1 -> rng.nextLong(1L << 31);
                    case 2 -> rng.nextLong(Long.MAX_VALUE);
                    default -> -rng.nextLong(1L << 40);
                };
                assertEquals(Arith.jacobi(BigInteger.valueOf(a), n), m.jacobi(a), "a=" + a + " n=" + n);
            }
        }
    }

    @Test
    void agreesOnEveryPatchAddressOfAnOracleModulus() {
        BigInteger n = new BigInteger((String) Golden.<java.util.Map<String, Object>>list("modulus").get(3).get("n"));
        OddModulus m = new OddModulus(n);
        for (long a = 1; a <= 5000; a++) assertEquals(Arith.jacobi(a, n), m.jacobi(a), "a=" + a);
        assertEquals(353, m.leastNonResidue(Budget.forBits(n.bitLength())).getAsLong());
    }

    @Test
    void leastNonResidueStopsAtTheCap() {
        BigInteger n = BigInteger.valueOf(3).pow(2).multiply(BigInteger.valueOf(7).pow(2));
        assertEquals(java.util.OptionalLong.empty(), new OddModulus(n).leastNonResidue(1000));
        assertEquals(java.util.OptionalLong.of(7), new OddModulus(BigInteger.valueOf(3 * 5)).leastNonResidue(7));
        assertEquals(java.util.OptionalLong.empty(), new OddModulus(BigInteger.valueOf(3 * 5)).leastNonResidue(6));
    }

    @Test
    void refusesEvenModulus() {
        assertThrows(IllegalArgumentException.class, () -> new OddModulus(BigInteger.TEN));
    }
}

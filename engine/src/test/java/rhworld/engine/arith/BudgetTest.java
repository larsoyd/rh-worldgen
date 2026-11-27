package rhworld.engine.arith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import rhworld.engine.Golden;
import java.util.List;
import org.junit.jupiter.api.Test;

class BudgetTest {
    @Test
    void matchesOracleForEveryBitLengthUpTo20000() {
        List<List<Object>> cases = Golden.list("budget");
        assertEquals(19999, cases.size());
        for (List<Object> c : cases) {
            int k = ((BigInteger) c.get(0)).intValue();
            assertEquals(((BigInteger) c.get(1)).longValue(), Budget.forBits(k), "k=" + k);
        }
    }

    @Test
    void knownValues() {
        assertEquals(503_791, Budget.forBits(1024));
        assertEquals(2_015_165, Budget.forBits(2048));
        assertEquals(710, Budget.side(1024));
        assertEquals(1420, Budget.side(2048));
    }

    @Test
    void exactWhereDoubleArithmeticRoundsUp() {
        assertEquals(4_510_614_664L, Budget.forBits(96_893));
        assertEquals(13_554_817_724L, Budget.forBits(167_966));
        assertEquals(18_042_458_659L, Budget.forBits(193_786));
    }

    @Test
    void coversTheSquaredLogOfEveryModulusOfThatLength() {
        for (int k = 2; k < 1000; k++) {
            double top = Math.log(Math.scalb(1.0, k) - 1) * Math.log(Math.scalb(1.0, k) - 1);
            assertTrue(Budget.forBits(k) >= Math.floor(top) - 1, "k=" + k);
            assertTrue(Budget.forBits(k) < top + 1, "k=" + k);
        }
    }

    @Test
    void sideSquaredCoversBudget() {
        for (int k = 2; k < 5000; k++) {
            long b = Budget.forBits(k), s = Budget.side(k);
            assertTrue(s * s >= b && (s - 1) * (s - 1) < b, "k=" + k + " b=" + b + " s=" + s);
        }
    }

    @Test
    void refusesBitLengthsOutsideTheExactRange() {
        assertThrows(IllegalArgumentException.class, () -> Budget.forBits(1));
        assertThrows(IllegalArgumentException.class, () -> Budget.forBits(Budget.MAX_BITS + 1));
    }
}

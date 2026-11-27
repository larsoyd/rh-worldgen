package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DiceTest {
    private static byte[] seed() {
        byte[] s = new byte[Dice.SEED_BYTES];
        for (int i = 0; i < s.length; i++) s[i] = (byte) i;
        return s;
    }

    private static int ones(long[] w) {
        int c = 0;
        for (long x : w) c += Long.bitCount(x);
        return c;
    }

    @Test
    void matchesTheOracleVector() {
        assertArrayEquals(new long[] {-4359226908302620812L, 17293095169L}, Dice.bits(seed(), 7, 100));
        long[] full = Dice.bits(seed(), 7, 41816);
        assertEquals(654, full.length);
        assertEquals(20991, ones(full));
        assertEquals(0x31c9a4L, full[full.length - 1]);
    }

    @Test
    void bitsPastTheCountAreZero() {
        long[] w = Dice.bits(seed(), 3, 70);
        assertEquals(0, w[1] >>> 6);
    }

    @Test
    void indexChangesTheDraw() {
        assertFalse(Arrays.equals(Dice.bits(seed(), 0, 256), Dice.bits(seed(), 1, 256)));
    }

    @Test
    void wrongSeedLengthIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Dice.bits(new byte[16], 0, 8));
        assertEquals("refused: seed must be 32 bytes length=16", e.getMessage());
    }
}

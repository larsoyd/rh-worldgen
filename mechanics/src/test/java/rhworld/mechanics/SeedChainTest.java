package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeedChainTest {
    private static byte[] top() {
        byte[] t = new byte[32];
        t[31] = 1;
        return t;
    }

    @Test
    void commitmentIsTheHashChainFoot() {
        SeedChain c = new SeedChain(top(), 3);
        assertEquals("0000000000000000000000000000000000000000000000000000000000000001", c.opening(2));
        assertEquals("ec4916dd28fc4c10d78e287ca5d9cc51ee1ae73cbfde08c6b37324cbfaac8bc5", c.opening(1));
        assertEquals(64, c.commitment().length());
        assertTrue(SeedChain.follows(c.commitment(), c.opening(0)));
        assertTrue(SeedChain.follows(c.opening(0), c.opening(1)));
        assertFalse(SeedChain.follows(c.commitment(), c.opening(1)));
        assertFalse(SeedChain.follows(c.opening(1), c.opening(0)));
    }

    @Test
    void exhaustedChainIsRefused() {
        SeedChain c = new SeedChain(top(), 3);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> c.opening(3));
        assertEquals("refused: seed chain exhausted ride=3 length=3", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new SeedChain(new byte[31], 3));
    }

    @Test
    void malformedOpeningNeverFollows() {
        SeedChain c = new SeedChain(top(), 2);
        assertFalse(SeedChain.follows(c.commitment(), "zz"));
        assertFalse(SeedChain.follows(c.commitment(), c.opening(0).toUpperCase() + "00"));
    }
}

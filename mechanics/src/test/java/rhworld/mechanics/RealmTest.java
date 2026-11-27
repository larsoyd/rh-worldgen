package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RealmTest {
    @Test
    void symbolsPickTheRealm() {
        assertEquals(Realm.HEARTH, Realm.of(1, 1));
        assertEquals(Realm.EMBER, Realm.of(-1, -1));
        assertEquals(Realm.MIST, Realm.of(1, -1));
        assertEquals(Realm.FROST, Realm.of(-1, 1));
    }

    @Test
    void zeroSymbolIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Realm.of(0, 1));
        assertEquals("refused: symbol not a unit p=0 q=1", e.getMessage());
    }

    @Test
    void colourIsTheProductOfTheSymbols() {
        for (Realm r : Realm.values()) {
            int product = (r.flipP() ? -1 : 1) * (r.flipQ() ? -1 : 1);
            assertEquals(Colour.ofJacobi(product), r.colour(), r.name());
        }
        assertEquals(Colour.WARM, Colour.ofJacobi(1));
        assertEquals(Colour.COLD, Colour.ofJacobi(-1));
        assertThrows(IllegalArgumentException.class, () -> Colour.ofJacobi(0));
    }

    @Test
    void shiftsAddLikeV4() {
        for (Realm a : Realm.values()) {
            assertEquals(a, a.plus(Realm.HEARTH));
            assertEquals(Realm.HEARTH, a.plus(a));
            for (Realm b : Realm.values()) {
                assertEquals(a.plus(b), b.plus(a));
                assertEquals(Realm.of(sign(a.flipP() ^ b.flipP()), sign(a.flipQ() ^ b.flipQ())), a.plus(b));
            }
        }
    }

    private static int sign(boolean flip) {
        return flip ? -1 : 1;
    }
}

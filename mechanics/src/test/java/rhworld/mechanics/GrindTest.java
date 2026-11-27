package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;

class GrindTest {
    private static final Warden RIGGED = Keys.sample("rigged-1024");
    private static final WildGate GATE = WildGate.forModulus(RIGGED.modulus());
    private static final int TRIALS = 200;
    private static final int CANDIDATES = 16;

    private static int embers(ToLongFunction<BigInteger> score) {
        int embers = 0;
        for (int t = 0; t < TRIALS; t++) {
            BigInteger best = null;
            long bestScore = Long.MIN_VALUE;
            for (int c = 0; c < CANDIDATES; c++) {
                byte[] top = new byte[Dice.SEED_BYTES];
                top[0] = (byte) t;
                top[1] = (byte) (t >> 8);
                top[2] = (byte) c;
                String opening = new SeedChain(top, 1).opening(0);
                BigInteger after = GATE.ride(BigInteger.ONE, Colour.WARM, Dice.forOpening(opening, 0, GATE.size()));
                long s = score.applyAsLong(after);
                if (s > bestScore) {
                    bestScore = s;
                    best = after;
                }
            }
            if (RIGGED.realm(best) == Realm.EMBER) embers++;
        }
        return embers;
    }

    @Test
    void grindingTopsOnPublicDataLeavesTheHiddenCoinFair() {
        int bySize = embers(x -> -x.bitLength() * 65536L - (x.longValue() & 0xffff));
        int byLowBits = embers(x -> x.longValue() & 0xff);
        System.out.println("[grind] trials=" + TRIALS + " candidates=" + CANDIDATES + " ember_by_size=" + bySize + " ember_by_low_bits=" + byLowBits);
        assertTrue(bySize >= 65 && bySize <= 135, "ember_by_size=" + bySize);
        assertTrue(byLowBits >= 65 && byLowBits <= 135, "ember_by_low_bits=" + byLowBits);
    }

    @Test
    void grindingWithTheFactorsPicksTheRealm() {
        int cheat = embers(x -> RIGGED.realm(x) == Realm.EMBER ? 1 : 0);
        assertTrue(cheat >= 195, "ember_with_factors=" + cheat);
        assertEquals(Colour.WARM, Realm.EMBER.colour());
    }
}

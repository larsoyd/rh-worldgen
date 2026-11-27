package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;
import rhworld.engine.arith.Primes;

class WildGateTest {
    private static final Warden RIGGED = Keys.sample("rigged-1024");
    private static final Warden RANDOM = Keys.sample("random-1024");
    private static final WildGate RIGGED_GATE = WildGate.forModulus(RIGGED.modulus());

    private static byte[] seed(int tag) {
        byte[] s = new byte[Dice.SEED_BYTES];
        s[0] = (byte) tag;
        return s;
    }

    @Test
    void fullGateHasTheGuaranteedSize() {
        assertEquals(41816, RIGGED_GATE.size());
        assertEquals(503791, RIGGED_GATE.primes()[RIGGED_GATE.size() - 1]);
        assertEquals(349, RIGGED_GATE.witness());
    }

    @Test
    void hiddenRowsSpanBothFactorsOnSampleKeys() {
        assertEquals(2, RIGGED.rank(RIGGED_GATE.primes()));
        assertEquals(2, RANDOM.rank(WildGate.forModulus(RANDOM.modulus()).primes()));
    }

    @Test
    void undersizedGateIsStuckOnTheRiggedKey() {
        WildGate small = new WildGate(RIGGED.modulus(), Primes.upTo(331));
        assertEquals(67, small.size());
        assertEquals(0, RIGGED.rank(small.primes()));
    }

    @Test
    void rideIsTheSteeredSubsetProduct() {
        BigInteger n = RIGGED.modulus();
        int[] primes = RIGGED_GATE.primes();
        BigInteger x = BigInteger.valueOf(12345);
        for (int r = 0; r < 20; r++) {
            long[] e = Dice.bits(seed(r), r, primes.length);
            for (Colour colour : Colour.values()) {
                BigInteger expected = x;
                for (int i = 0; i < primes.length; i++) {
                    if ((e[i >>> 6] >>> (i & 63) & 1) == 1) expected = expected.multiply(BigInteger.valueOf(primes[i])).mod(n);
                }
                if (Colour.ofJacobi(Arith.jacobi(expected, n)) != colour) {
                    int w = Arrays.binarySearch(primes, (int) RIGGED_GATE.witness());
                    boolean set = (e[w >>> 6] >>> (w & 63) & 1) == 1;
                    BigInteger wb = BigInteger.valueOf(RIGGED_GATE.witness());
                    expected = set ? expected.multiply(wb.modInverse(n)).mod(n) : expected.multiply(wb).mod(n);
                }
                assertEquals(expected, RIGGED_GATE.ride(x, colour, e), "ride=" + r + " colour=" + colour);
            }
        }
    }

    @Test
    void ridesFollowTheColourAndReachEveryRealm() {
        BigInteger n = RIGGED.modulus();
        Map<Realm, Integer> seen = new EnumMap<>(Realm.class);
        BigInteger x = BigInteger.ONE;
        for (int r = 0; r < 400; r++) {
            Colour colour = r % 2 == 0 ? Colour.WARM : Colour.COLD;
            x = RIGGED_GATE.ride(x, colour, Dice.bits(seed(99), r, RIGGED_GATE.size()));
            assertEquals(colour, Colour.ofJacobi(Arith.jacobi(x, n)), "ride=" + r);
            Realm realm = RIGGED.realm(x);
            assertEquals(colour, realm.colour(), "ride=" + r);
            seen.merge(realm, 1, Integer::sum);
        }
        for (Realm realm : Realm.values()) {
            int c = seen.getOrDefault(realm, 0);
            assertTrue(c >= 60 && c <= 140, "realm=" + realm + " count=" + c + " of 200 per colour");
        }
    }

    @Test
    void undersizedGateHidesTwoRealmsOnTheRiggedKey() {
        WildGate small = new WildGate(RIGGED.modulus(), Primes.upTo(349));
        assertEquals(70, small.size());
        assertEquals(1, RIGGED.rank(small.primes()));
        BigInteger x = BigInteger.ONE;
        for (int r = 0; r < 200; r++) {
            Colour colour = r % 2 == 0 ? Colour.WARM : Colour.COLD;
            x = small.ride(x, colour, Dice.bits(seed(7), r, small.size()));
            assertEquals(colour == Colour.WARM ? Realm.HEARTH : Realm.FROST, RIGGED.realm(x), "ride=" + r);
        }
    }

    @Test
    void wrongDiceLengthIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RIGGED_GATE.ride(BigInteger.ONE, Colour.WARM, new long[3]));
        assertEquals("refused: dice length words=3 expected=654", e.getMessage());
        long[] dirty = new long[654];
        dirty[653] = 1L << 63;
        e = assertThrows(IllegalArgumentException.class, () -> RIGGED_GATE.ride(BigInteger.ONE, Colour.WARM, dirty));
        assertEquals("refused: dice bits past the gate size size=41816", e.getMessage());
    }

    @Test
    void attunementOutOfRangeIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RIGGED_GATE.ride(BigInteger.ZERO, Colour.WARM, new long[654]));
        assertEquals("refused: attunement not a unit below n", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RIGGED_GATE.ride(RIGGED.modulus(), Colour.WARM, new long[654]));
    }

    @Test
    void gateWithoutAWitnessIsRefused() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new WildGate(RIGGED.modulus(), Primes.upTo(331)).ride(BigInteger.ONE, Colour.COLD, new long[2]));
        assertEquals("refused: budget exceeded no jacobi -1 prime in the gate size=67", e.getMessage());
    }
}

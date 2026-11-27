package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.OptionalLong;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class AuditTest {
    private static final Warden RIGGED = Keys.sample("rigged-1024");
    private static final WildGate GATE = WildGate.forModulus(RIGGED.modulus());

    private static RideLog session(int count, int tag, UnaryOperator<Realm> report, boolean warmOnly) {
        SeedChain chain = chain(tag, count);
        RideLog log = new RideLog(RIGGED.modulus(), chain.commitment());
        BigInteger x = BigInteger.ONE;
        for (int r = 0; r < count; r++) {
            Colour colour = warmOnly || r % 2 == 0 ? Colour.WARM : Colour.COLD;
            String opening = chain.opening(r);
            BigInteger after = GATE.ride(x, colour, Dice.forOpening(opening, r, GATE.size()));
            log.add(new Ride(r, colour, opening, x, after, report.apply(RIGGED.realm(after))));
            x = after;
        }
        return log;
    }

    private static SeedChain chain(int tag, int rides) {
        byte[] top = new byte[Dice.SEED_BYTES];
        top[1] = (byte) tag;
        return new SeedChain(top, rides);
    }

    private static Realm hideEmber(Realm r) {
        return r == Realm.EMBER ? Realm.HEARTH : r;
    }

    @Test
    void honestServerIsNeverAccused() {
        for (int tag = 0; tag < 2; tag++) {
            Audit.Verdict v = Audit.of(session(2000, tag, r -> r, false), 0x1p-30);
            assertFalse(v.accused(), v.line());
            assertEquals(0, v.publicMismatches());
            assertEquals(1000, v.rides().get(Colour.WARM));
        }
    }

    @Test
    void hiddenRealmIsAccusedWithinTwentyWarmRides() {
        Audit.Verdict v = Audit.of(session(20, 1, AuditTest::hideEmber, true), 0x1p-16);
        assertTrue(v.accused(), v.line());
        assertEquals(Realm.EMBER, v.weakest());
        assertEquals(0, v.publicMismatches());
        assertEquals(4 * Math.pow(2, -20), v.pValue(), 1e-18);
        assertEquals("[audit] rides_warm=20 rides_cold=0 hearth=20 ember=0 mist=0 frost=0 mismatches=0 weakest=ember "
                + "p=3.814697265625E-6 verdict=accused", v.line());
    }

    @Test
    void lopsidedReportsAreAccusedByTheBinomialTail() {
        int[] count = {0};
        UnaryOperator<Realm> skew = r -> r == Realm.EMBER && count[0]++ % 3 != 0 ? Realm.HEARTH : r;
        Audit.Verdict v = Audit.of(session(600, 2, skew, true), 0x1p-20);
        assertTrue(v.accused(), v.line());
        assertEquals(Realm.EMBER, v.weakest());
    }

    @Test
    void reportOfTheWrongColourIsAPublicMismatch() {
        Audit.Verdict v = Audit.of(session(10, 3, r -> r == Realm.EMBER ? Realm.MIST : r, true), 0x1p-30);
        assertTrue(v.publicMismatches() > 0, v.line());
        assertTrue(v.accused(), v.line());
    }

    @Test
    void replayNamesTheFirstFalseReport() {
        RideLog honest = session(50, 4, r -> r, false);
        assertEquals(OptionalLong.empty(), Audit.replay(RIGGED, honest));
        int[] k = {0};
        RideLog one = session(50, 4, r -> k[0]++ == 17 ? r.plus(Realm.EMBER) : r, false);
        assertEquals(OptionalLong.of(17), Audit.replay(RIGGED, one));
    }

    @Test
    void replayRefusesAnotherKey() {
        Warden other = Keys.sample("random-1024");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Audit.replay(other, session(2, 5, r -> r, false)));
        assertEquals("refused: log modulus differs from the revealed key", e.getMessage());
    }

    @Test
    void logRoundTripsThroughCanonicalJson() {
        RideLog log = session(5, 6, r -> r, false);
        String json = log.toJson();
        assertEquals(json, RideLog.fromJson(json).toJson());
        assertEquals(log.rides(), RideLog.fromJson(json).rides());
        assertTrue(json.startsWith("{\"commitment\":\"" + chain(6, 5).commitment() + "\",\"format\":\"rhworld.rides/1\",\"n\":\"" + RIGGED.modulus() + "\",\"rides\":[{\"after\":\""), json);
    }

    @Test
    void logRefusesAGapInTheIndex() {
        SeedChain c = chain(9, 3);
        RideLog log = new RideLog(BigInteger.valueOf(15), c.commitment());
        log.add(new Ride(0, Colour.WARM, c.opening(0), BigInteger.ONE, BigInteger.TWO, Realm.HEARTH));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> log.add(new Ride(2, Colour.WARM, c.opening(2), BigInteger.TWO, BigInteger.TWO, Realm.HEARTH)));
        assertEquals("refused: ride index out of order index=2 expected=1", e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                () -> log.add(new Ride(1, Colour.WARM, c.opening(1), BigInteger.ONE, BigInteger.TWO, Realm.HEARTH)));
        assertEquals("refused: ride does not start where the last ended index=1", e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                () -> log.add(new Ride(1, Colour.WARM, c.opening(2), BigInteger.TWO, BigInteger.TWO, Realm.HEARTH)));
        assertEquals("refused: opening does not follow the chain index=1", e.getMessage());
    }
}
